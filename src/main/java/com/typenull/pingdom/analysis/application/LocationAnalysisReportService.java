package com.typenull.pingdom.analysis.application;

import com.typenull.pingdom.analysis.api.dto.LocationAnalysisRequest;
import com.typenull.pingdom.analysis.application.ai.AiAnalysisClient;
import com.typenull.pingdom.analysis.application.ai.AiAnalysisResponse;
import com.typenull.pingdom.analysis.application.ai.LocationAnalysisContent;
import com.typenull.pingdom.analysis.application.ai.LocationAnalysisPromptFactory;
import com.typenull.pingdom.analysis.application.ai.LocationAnalysisResponseValidator;
import com.typenull.pingdom.analysis.application.pdf.HtmlToPdfConverter;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 분석 조건을 AI에 전달하고 응답 검증, 주변 장소 보강, 파생 표시값 계산, XHTML·PDF 생성을 순서대로 수행합니다.
 * 외부 AI 호출과 PDF 변환을 DB 트랜잭션으로 묶지 않으며, 결과 저장은 호출자의 보관 서비스가 담당합니다.
 * 각 단계의 실패는 요청 실패로 전파되고 이 서비스 자체의 재시도·결과 캐시는 없습니다.
 */
@Service
@Slf4j
public class LocationAnalysisReportService {

    private final LocationAnalysisPromptFactory promptFactory;
    private final AiAnalysisClient aiAnalysisClient;
    private final LocationAnalysisResponseValidator responseValidator;
    private final LocationAnalysisCompetitionService competitionService;
    private final LocationAnalysisHtmlComposer htmlComposer;
    private final HtmlToPdfConverter htmlToPdfConverter;
    private final Clock clock;

    public LocationAnalysisReportService(
            LocationAnalysisPromptFactory promptFactory,
            AiAnalysisClient aiAnalysisClient,
            LocationAnalysisResponseValidator responseValidator,
            LocationAnalysisCompetitionService competitionService,
            LocationAnalysisHtmlComposer htmlComposer,
            HtmlToPdfConverter htmlToPdfConverter,
            Clock clock
    ) {
        this.promptFactory = promptFactory;
        this.aiAnalysisClient = aiAnalysisClient;
        this.responseValidator = responseValidator;
        this.competitionService = competitionService;
        this.htmlComposer = htmlComposer;
        this.htmlToPdfConverter = htmlToPdfConverter;
        this.clock = clock;
    }

    /**
     * 요청 기준일로 AI 분석을 실행·검증하고 주변 경쟁 정보를 보강한 뒤 고정 XHTML과 PDF를 생성한다.
     * AI가 기준일을 생략하면 요청 시 기준일을 사용하며, 생성 ID·이름·본문·발행일을 반환한다. 단계 실패는 전파되고 보고서 보관은 별도 호출자가 담당한다.
     */
    public LocationAnalysisPdf generate(LocationAnalysisRequest request) {
        long startedAt = System.nanoTime();
        LocalDate analysisBasisDate = LocalDate.now(clock);
        AiAnalysisResponse aiResponse = aiAnalysisClient.analyze(
                promptFactory.create(request, analysisBasisDate)
        );
        long aiCompletedAt = System.nanoTime();
        LocationAnalysisContent content = aiResponse.content();
        String grade = content != null && content.overallLocationEvaluation() != null
                && content.overallLocationEvaluation().grade() != null
                ? content.overallLocationEvaluation().grade().name() : "missing";
        int recommendationCount = content == null || content.recommendedPlaces() == null
                ? 0 : content.recommendedPlaces().size();
        Double trafficTotal = content != null && content.footTrafficAnalysis() != null
                ? content.footTrafficAnalysis().total() : null;
        Double analysisRadius = content != null && content.analysisScope() != null
                ? content.analysisScope().radiusMeters() : null;
        log.info("입지 분석 AI 결과 수신. grade={}, recommendationCount={}, trafficTotal={}, analysisRadiusMeters={}",
                grade, recommendationCount, trafficTotal, analysisRadius);
        // PDF 디자인과 한글 폰트를 요청마다 동일하게 유지하기 위해 AI가 반환한 HTML은 사용하지 않는다.
        try {
            responseValidator.validate(request, aiResponse);
        } catch (RuntimeException exception) {
            log.warn("입지 분석 AI 결과 검증 실패. grade={}, recommendationCount={}, trafficTotal={}",
                    grade, recommendationCount, trafficTotal);
            throw exception;
        }
        content = competitionService.enrich(content, request.getCategory())
                .withDerivedReportMetrics()
                .withDerivedBusinessPerformance();
        String reportId = UUID.randomUUID().toString();
        LocalDate publishedDate = LocalDate.now(clock);
        LocalDate effectiveAnalysisBasisDate = aiResponse.analysisBasisDate() == null
                ? analysisBasisDate : aiResponse.analysisBasisDate();
        String html = htmlComposer.compose(
                reportId,
                aiResponse.reportName(),
                publishedDate,
                effectiveAnalysisBasisDate,
                content
        );
        byte[] pdf = htmlToPdfConverter.convert(html);
        long completedAt = System.nanoTime();
        log.info(
                "입지 분석 보고서 생성 완료. reportId={}, aiMs={}, pdfMs={}, htmlLength={}, pdfBytes={}",
                reportId,
                elapsedMillis(startedAt, aiCompletedAt),
                elapsedMillis(aiCompletedAt, completedAt),
                html.length(),
                pdf.length
        );
        return new LocationAnalysisPdf(
                pdf, reportId, aiResponse.reportName(), html, publishedDate, effectiveAnalysisBasisDate
        );
    }

    private long elapsedMillis(long startedAt, long completedAt) {
        return (completedAt - startedAt) / 1_000_000;
    }

    public record LocationAnalysisPdf(
            byte[] content,
            String reportId,
            String reportName,
            String html,
            LocalDate publishedDate,
            LocalDate analysisBasisDate
    ) {
        public LocationAnalysisPdf(byte[] content, String reportId, String reportName) {
            this(content, reportId, reportName, null, null, null);
        }
    }

}
