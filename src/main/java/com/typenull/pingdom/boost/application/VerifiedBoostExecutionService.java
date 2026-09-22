package com.typenull.pingdom.boost.application;

import com.typenull.pingdom.boost.api.dto.VerifiedBoostExecutionPageResponse;
import com.typenull.pingdom.boost.api.dto.VerifiedBoostExecutionResponse;
import com.typenull.pingdom.boost.api.dto.VerifiedBoostExecutionStartRequest;
import com.typenull.pingdom.boost.domain.MerchantVerifiedBoostSelection;
import com.typenull.pingdom.boost.domain.VerifiedBoostExecution;
import com.typenull.pingdom.boost.domain.VerifiedBoostProduct;
import com.typenull.pingdom.boost.domain.exception.VerifiedBoostErrorCode;
import com.typenull.pingdom.boost.domain.exception.VerifiedBoostException;
import com.typenull.pingdom.boost.infrastructure.MerchantVerifiedBoostSelectionRepository;
import com.typenull.pingdom.boost.infrastructure.VerifiedBoostExecutionRepository;
import com.typenull.pingdom.boost.infrastructure.VerifiedBoostProductRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 선택한 상품의 기간으로 장소 부스트를 시작·중단하고 시점별 상태를 조회합니다.
 * 시작은 장소 소유 행, 중단은 집행 행과 장소 소유 행을 잠그며, 실제 결제 승인이나 추천 점수 계산은 수행하지 않습니다.
 */
@Service
@RequiredArgsConstructor
public class VerifiedBoostExecutionService {

    private final VerifiedBoostExecutionRepository executionRepository;
    private final MerchantVerifiedBoostSelectionRepository selectionRepository;
    private final VerifiedBoostProductRepository productRepository;
    private final VerifiedBoostAccessPolicy accessPolicy;
    private final VerifiedBoostQualityGuardrail qualityGuardrail;
    private final Clock clock;

    /**
     * 동일 선택의 기존 집행이 있으면 만료·중단 여부와 무관하게 현재 상태를 반환합니다.
     * 신규 집행만 운영 품질과 같은 장소의 활성 집행 중복을 검사하고, 상품의 현재 durationDays를 적용합니다.
     */
    @Transactional
    public VerifiedBoostExecutionResponse start(Long ownerId, VerifiedBoostExecutionStartRequest request) {
        LocalDateTime now = LocalDateTime.now(clock);
        MerchantVerifiedBoostSelection selection = selectionRepository
                .findByIdAndMerchantOwnerUserId(request.selectionId(), ownerId)
                .orElseThrow(() -> new VerifiedBoostException(VerifiedBoostErrorCode.SELECTION_NOT_FOUND));
        var ownerPlace = accessPolicy.requireOwnedPlaceForUpdate(ownerId, selection.getPlaceId(), now);

        var existing = executionRepository.findBySelectionId(selection.getId());
        if (existing.isPresent()) {
            return VerifiedBoostExecutionResponse.from(existing.get(), now);
        }

        qualityGuardrail.requireEligible(ownerPlace);
        if (executionRepository.findActiveByPlaceId(selection.getPlaceId(), now).isPresent()) {
            throw new VerifiedBoostException(VerifiedBoostErrorCode.EXECUTION_ALREADY_ACTIVE);
        }
        VerifiedBoostProduct product = productRepository.findById(selection.getProductId())
                .orElseThrow(() -> new VerifiedBoostException(VerifiedBoostErrorCode.PRODUCT_NOT_FOUND));
        VerifiedBoostExecution execution = VerifiedBoostExecution.start(selection, product.getDurationDays(), now);
        return VerifiedBoostExecutionResponse.from(executionRepository.save(execution), now);
    }

    /**
     * 요청 점주의 집행 행을 잠그고 현재 장소 소유 자격을 재확인한 뒤 집행을 중단해 현재 시점의 응답을 반환합니다.
     * 대상 부재·소유권 오류는 거절하며 중단 불가 상태는 INVALID_EXECUTION_STATE로 변환합니다.
     */
    @Transactional
    public VerifiedBoostExecutionResponse stop(Long ownerId, Long executionId) {
        LocalDateTime now = LocalDateTime.now(clock);
        VerifiedBoostExecution execution = executionRepository.findOwnedByIdForUpdate(executionId, ownerId)
                .orElseThrow(() -> new VerifiedBoostException(VerifiedBoostErrorCode.EXECUTION_NOT_FOUND));
        accessPolicy.requireOwnedPlaceForUpdate(ownerId, execution.getPlaceId(), now);
        try {
            execution.stop(now);
        } catch (IllegalStateException exception) {
            throw new VerifiedBoostException(VerifiedBoostErrorCode.INVALID_EXECUTION_STATE);
        }
        return VerifiedBoostExecutionResponse.from(execution, now);
    }

    /**
     * 지정 점주의 부스트 집행을 시작 시각·ID 내림차순으로 조회하고 동일 Clock 시각으로 계산한 집행 상태를 반환합니다.
     * 페이지는 최소 1, 크기는 1~100으로 보정하며 종료·중단 이력도 조회 대상에 포함합니다.
     */
    @Transactional(readOnly = true)
    public VerifiedBoostExecutionPageResponse list(Long ownerId, int page, int limit) {
        LocalDateTime now = LocalDateTime.now(clock);
        Page<VerifiedBoostExecution> result = executionRepository.findAllByMerchantOwnerUserId(ownerId,
                PageRequest.of(Math.max(page - 1, 0), Math.min(Math.max(limit, 1), 100),
                        Sort.by(Sort.Order.desc("startedAt"), Sort.Order.desc("id"))));
        return new VerifiedBoostExecutionPageResponse(
                result.getContent().stream().map(execution -> VerifiedBoostExecutionResponse.from(execution, now)).toList(),
                result.getNumber() + 1, result.getSize(), result.getTotalElements(), result.getTotalPages(),
                result.hasNext());
    }
}
