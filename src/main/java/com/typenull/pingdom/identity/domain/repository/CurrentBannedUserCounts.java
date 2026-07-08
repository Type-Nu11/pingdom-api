package com.typenull.pingdom.identity.domain.repository;

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
