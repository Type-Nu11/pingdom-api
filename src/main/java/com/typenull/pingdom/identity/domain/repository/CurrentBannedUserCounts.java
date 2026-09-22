package com.typenull.pingdom.identity.domain.repository;

/**
 * 현재 정지 회원의 전체·영구·기간제 집계 결과입니다.
 * SUM 결과가 null인 빈 집계도 각 항목을 0으로 변환해 반환합니다.
 */
public record CurrentBannedUserCounts(
        long total,
        long permanent,
        long temporary
) {
    public CurrentBannedUserCounts(Long total, Long permanent, Long temporary) {
        this(
                total == null ? 0L : total,
                permanent == null ? 0L : permanent,
                temporary == null ? 0L : temporary
        );
    }
}
