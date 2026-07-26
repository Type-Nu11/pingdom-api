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

@Service
@RequiredArgsConstructor
public class VerifiedBoostExecutionService {

    private final VerifiedBoostExecutionRepository executionRepository;
    private final MerchantVerifiedBoostSelectionRepository selectionRepository;
    private final VerifiedBoostProductRepository productRepository;
    private final VerifiedBoostAccessPolicy accessPolicy;
    private final VerifiedBoostQualityGuardrail qualityGuardrail;
    private final Clock clock;

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
