package com.typenull.pingdom.boost.application;

import com.typenull.pingdom.boost.api.dto.VerifiedBoostProductCreateRequest;
import com.typenull.pingdom.boost.api.dto.VerifiedBoostProductPageResponse;
import com.typenull.pingdom.boost.api.dto.VerifiedBoostProductResponse;
import com.typenull.pingdom.boost.domain.VerifiedBoostProduct;
import com.typenull.pingdom.boost.domain.VerifiedBoostProductStatus;
import com.typenull.pingdom.boost.domain.exception.VerifiedBoostErrorCode;
import com.typenull.pingdom.boost.domain.exception.VerifiedBoostException;
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
 * 관리자 부스트 상품의 초안·활성 상태를 저장하고 점주용 활성 목록을 제공합니다.
 * 상태 변경은 상품 쓰기 잠금으로 처리하며, 기존 선택이나 집행 이력을 함께 변경하지 않습니다.
 */
@Service
@RequiredArgsConstructor
public class VerifiedBoostProductService {

    private final VerifiedBoostProductRepository repository;
    private final Clock clock;

    /**
     * 이름·설명·가격·기간을 도메인에서 검증해 부스트 상품 초안을 저장하고 응답을 반환합니다.
     * 입력 조건 위반은 INVALID_PRODUCT_INPUT으로 변환하며 생성만으로 상품을 활성화하지 않습니다.
     */
    @Transactional
    public VerifiedBoostProductResponse create(VerifiedBoostProductCreateRequest request) {
        LocalDateTime now = LocalDateTime.now(clock);
        try {
            return VerifiedBoostProductResponse.from(repository.save(VerifiedBoostProduct.draft(
                    request.name(), request.description(), request.priceAmount(),
                    request.durationDays(), now)));
        } catch (IllegalArgumentException exception) {
            throw new VerifiedBoostException(VerifiedBoostErrorCode.INVALID_PRODUCT_INPUT);
        }
    }

    /**
     * 활성 여부와 무관하게 부스트 상품을 생성 시각·ID 내림차순으로 조회해 페이지와 전체 건수를 반환합니다.
     * 외부 페이지는 최소 1, 크기는 1~100으로 보정합니다.
     */
    @Transactional(readOnly = true)
    public VerifiedBoostProductPageResponse list(int page, int limit) {
        Page<VerifiedBoostProduct> result = repository.findAll(
                PageRequest.of(Math.max(page - 1, 0), Math.min(Math.max(limit, 1), 100),
                        Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
        return new VerifiedBoostProductPageResponse(
                result.getContent().stream().map(VerifiedBoostProductResponse::from).toList(),
                result.getNumber() + 1, result.getSize(), result.getTotalElements(), result.getTotalPages(),
                result.hasNext());
    }

    /**
     * 점주에게 선택 가능한 ACTIVE 부스트 상품만 생성 시각·ID 내림차순으로 조회해 페이지 정보를 반환합니다.
     * 외부 페이지는 최소 1, 크기는 1~100으로 보정합니다.
     */
    @Transactional(readOnly = true)
    public VerifiedBoostProductPageResponse listActive(int page, int limit) {
        Page<VerifiedBoostProduct> result = repository.findAllByStatus(VerifiedBoostProductStatus.ACTIVE,
                PageRequest.of(Math.max(page - 1, 0), Math.min(Math.max(limit, 1), 100),
                        Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
        return new VerifiedBoostProductPageResponse(
                result.getContent().stream().map(VerifiedBoostProductResponse::from).toList(),
                result.getNumber() + 1, result.getSize(), result.getTotalElements(), result.getTotalPages(),
                result.hasNext());
    }

    @Transactional(readOnly = true)
    public VerifiedBoostProductResponse get(Long productId) {
        return VerifiedBoostProductResponse.from(repository.findById(productId)
                .orElseThrow(() -> new VerifiedBoostException(VerifiedBoostErrorCode.PRODUCT_NOT_FOUND)));
    }

    /**
     * 상품 행을 잠가 활성화하고 변경 결과를 반환합니다.
     * 상품 부재는 PRODUCT_NOT_FOUND, 활성화할 수 없는 상태는 INVALID_PRODUCT_STATE로 거절합니다.
     */
    @Transactional
    public VerifiedBoostProductResponse activate(Long productId) {
        LocalDateTime now = LocalDateTime.now(clock);
        VerifiedBoostProduct product = findForUpdate(productId);
        try {
            product.activate(now);
        } catch (IllegalStateException exception) {
            throw new VerifiedBoostException(VerifiedBoostErrorCode.INVALID_PRODUCT_STATE);
        }
        return VerifiedBoostProductResponse.from(product);
    }

    /**
     * 상품 행을 잠가 비활성화하고 변경 결과를 반환합니다.
     * 상품 부재와 불가능한 상태 전이는 거절하며 기존 선택·집행 이력은 이 메서드에서 변경하지 않습니다.
     */
    @Transactional
    public VerifiedBoostProductResponse deactivate(Long productId) {
        LocalDateTime now = LocalDateTime.now(clock);
        VerifiedBoostProduct product = findForUpdate(productId);
        try {
            product.deactivate(now);
        } catch (IllegalStateException exception) {
            throw new VerifiedBoostException(VerifiedBoostErrorCode.INVALID_PRODUCT_STATE);
        }
        return VerifiedBoostProductResponse.from(product);
    }

    private VerifiedBoostProduct findForUpdate(Long productId) {
        return repository.findByIdForUpdate(productId)
                .orElseThrow(() -> new VerifiedBoostException(VerifiedBoostErrorCode.PRODUCT_NOT_FOUND));
    }
}
