package com.typenull.pingdom.post.infrastructure.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.post.infrastructure.persistence.MapImageRepository;
import com.typenull.pingdom.shared.support.S3ObjectStorage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class MapImageS3OrphanReportServiceTest {

    @Mock
    private S3ObjectStorage s3ObjectStorage;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private MapImageRepository mapImageRepository;

    @Mock
    private TaskExecutor orphanReportExecutor;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    @Mock
    private ListOperations<String, String> listOperations;

    @Mock
    private SetOperations<String, String> setOperations;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private MapImageS3OrphanReportService service;

    /**
     * S3·Redis·사진 저장소·작업 executor를 대역으로 연결해 고아 객체 보고와 삭제 판단을 분리 검증.
     */
    @BeforeEach
    void setUp() {
        service = new MapImageS3OrphanReportService(
                s3ObjectStorage,
                redisTemplate,
                mapImageRepository,
                orphanReportExecutor
        );
    }

    /**
     * S3 3개 키를 원본·썸네일 DB 참조와 함께 비교해 DB 2건·고아 1건 및 고아 키 목록을 계산하는지 검증.
     */
    @Test
    void comparesOriginalAndThumbnailUsage() {
        List<String> listedKeys = List.of("map/used.jpg", "map/orphan.jpg", "map/thumbnails/used-thumb.jpg");
        when(s3ObjectStorage.listKeys("map/", 100)).thenReturn(new S3ObjectStorage.S3ListResult(listedKeys, false));
        when(mapImageRepository.findUsedOriginalS3Keys(listedKeys)).thenReturn(List.of("map/used.jpg"));
        when(mapImageRepository.findUsedThumbnailS3Keys(listedKeys))
                .thenReturn(List.of("map/thumbnails/used-thumb.jpg"));
        when(mapImageRepository.countOriginalS3Keys()).thenReturn(1L);
        when(mapImageRepository.countThumbnailS3Keys()).thenReturn(1L);

        MapImageS3OrphanReportService.S3OrphanDryRunReport report = service.reportOrphanObjects("map/", 100);

        assertEquals("map/", report.prefix());
        assertEquals(2, report.dbKeyCount());
        assertEquals(3, report.s3ObjectCount());
        assertEquals(1, report.orphanObjectCount());
        assertEquals(List.of("map/orphan.jpg"), report.orphanKeys());
    }

    /**
     * 완료 Redis 메타데이터와 첫 후보 2건을 읽어 전체 후보 3건·2페이지·다음 있음 및 후보 키를 응답하는지 검증.
     */
    @Test
    void readsCachedOrphanReportPage() {
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(redisTemplate.opsForList()).thenReturn(listOperations);
        when(hashOperations.get(any(), any())).thenAnswer(invocation -> switch (String.valueOf((Object) invocation.getArgument(1))) {
            case "status" -> "COMPLETED";
            case "generatedAt" -> "2026-06-25T21:00:00";
            case "dbKeyCount" -> "2";
            case "s3KeyCount" -> "4";
            case "deleteCandidateCount" -> "3";
            default -> null;
        });
        when(listOperations.range(any(), eq(0L), eq(1L)))
                .thenReturn(List.of("map/orphan-1.jpg", "map/orphan-2.jpg"));

        MapImageS3OrphanReportService.S3OrphanReport report = service.getMapImageS3OrphanReport("report-1", 1, 2);

        assertEquals(2, report.dbKeyCount());
        assertEquals(4, report.s3KeyCount());
        assertEquals(3, report.deleteCandidateCount());
        assertEquals(2, report.totalPages());
        assertEquals(true, report.hasNext());
        assertEquals("map/orphan-1.jpg", report.deleteCandidates().getFirst().key());
    }

    /**
     * 보고서 후보 2개 중 현재 DB가 사용하는 키는 사유와 함께 실패로 남기고 미사용 키만 S3에서 삭제하는지 검증.
     */
    @Test
    void rechecksUsageBeforeOrphanDeletion() {
        stubCompletedReport();
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.isMember(any(), eq("map/orphan.jpg"))).thenReturn(true);
        when(setOperations.isMember(any(), eq("map/active.jpg"))).thenReturn(true);
        List<String> candidateKeys = List.of("map/orphan.jpg", "map/active.jpg");
        when(mapImageRepository.findUsedOriginalS3Keys(candidateKeys)).thenReturn(List.of("map/active.jpg"));
        when(mapImageRepository.findUsedThumbnailS3Keys(candidateKeys)).thenReturn(List.of());

        MapImageS3OrphanReportService.S3OrphanDeleteResult result =
                service.deleteMapImageS3Candidates("report-1", candidateKeys);

        assertEquals(2, result.requestedKeyCount());
        assertEquals(List.of("map/orphan.jpg"), result.deletedKeys());
        assertEquals(1, result.failedKeyCount());
        assertEquals("map/active.jpg", result.failedKeys().getFirst().key());
        assertEquals("DB(MapImage)에서 사용 중인 S3 객체입니다.", result.failedKeys().getFirst().reason());
        verify(s3ObjectStorage).delete("map/orphan.jpg");
        verify(s3ObjectStorage, never()).delete("map/active.jpg");
    }

    /**
     * 보고서 후보 집합에 없는 키는 실패 처리하고 S3 삭제·DB 사용 여부 조회를 하지 않는지 검증.
     */
    @Test
    void rejectsKeysOutsideReportCandidates() {
        stubCompletedReport();
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.isMember(any(), eq("map/expired.jpg"))).thenReturn(false);

        MapImageS3OrphanReportService.S3OrphanDeleteResult result =
                service.deleteMapImageS3Candidates("report-1", List.of("map/expired.jpg"));

        assertEquals(1, result.requestedKeyCount());
        assertEquals(0, result.deletedKeyCount());
        assertEquals(1, result.failedKeyCount());
        assertEquals("리포트 삭제 후보에 없는 S3 객체입니다.", result.failedKeys().getFirst().reason());
        verify(s3ObjectStorage, never()).delete(any());
        verify(mapImageRepository, never()).findUsedOriginalS3Keys(any());
    }

    /**
     * 실행 중 새로고침은 같은 RUNNING 보고서를 반환하고 작업은 한 번만 제출하는지 검증.
     * 보관한 Runnable을 완료한 뒤에는 새 보고서 ID로 다음 작업을 제출해야 함.
     */
    @Test
    void reusesRunningOrphanReport() {
        stubReportMetadata();
        List<Runnable> submittedTasks = new ArrayList<>();
        doAnswer(invocation -> {
            submittedTasks.add(invocation.getArgument(0));
            return null;
        }).when(orphanReportExecutor).execute(any(Runnable.class));
        when(s3ObjectStorage.listKeysPage("map/", null))
                .thenReturn(new S3ObjectStorage.S3KeyPage(List.of(), null));

        MapImageS3OrphanReportService.S3OrphanReportStatus first =
                service.refreshMapImageS3OrphanReport();
        MapImageS3OrphanReportService.S3OrphanReportStatus duplicate =
                service.refreshMapImageS3OrphanReport();

        assertEquals(first.reportId(), duplicate.reportId());
        assertEquals("RUNNING", duplicate.status());
        assertEquals(1, submittedTasks.size());

        submittedTasks.getFirst().run();
        MapImageS3OrphanReportService.S3OrphanReportStatus next =
                service.refreshMapImageS3OrphanReport();

        assertNotEquals(first.reportId(), next.reportId());
        assertEquals(2, submittedTasks.size());
    }

    /**
     * executor 대기열 거절 시 FAILED와 포화 메시지를 기록하고 다음 시도는 새 ID로 다시 제출하는지 검증.
     */
    @Test
    void failsRejectedReportAndRetries() {
        stubReportMetadata();
        doThrow(new TaskRejectedException("queue full"))
                .when(orphanReportExecutor)
                .execute(any(Runnable.class));

        MapImageS3OrphanReportService.S3OrphanReportStatus rejected =
                service.refreshMapImageS3OrphanReport();
        MapImageS3OrphanReportService.S3OrphanReportStatus retried =
                service.refreshMapImageS3OrphanReport();

        assertEquals("FAILED", rejected.status());
        assertEquals("S3 고아 파일 리포트 생성 대기열이 포화되었습니다.", rejected.errorMessage());
        assertEquals("FAILED", retried.status());
        assertNotEquals(rejected.reportId(), retried.reportId());
        verify(orphanReportExecutor, org.mockito.Mockito.times(2)).execute(any(Runnable.class));
    }

    /**
     * 삭제 후보 검증이 실행될 수 있도록 Redis 보고서 상태를 COMPLETED로 설정.
     */
    private void stubCompletedReport() {
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.get(any(), eq("status"))).thenReturn("COMPLETED");
    }

    /**
     * Redis hash 쓰기·읽기를 메모리 맵으로 연결해 비동기 보고서 상태 변경을 테스트 안에서 재현.
     */
    private void stubReportMetadata() {
        Map<String, Map<Object, Object>> metadata = new HashMap<>();
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        doAnswer(invocation -> {
            String key = invocation.getArgument(0);
            Object field = invocation.getArgument(1);
            Object value = invocation.getArgument(2);
            metadata.computeIfAbsent(key, ignored -> new HashMap<>()).put(field, value);
            return null;
        }).when(hashOperations).put(anyString(), any(), any());
        when(hashOperations.get(anyString(), any())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            Object field = invocation.getArgument(1);
            return metadata.getOrDefault(key, Map.of()).get(field);
        });
    }
}
