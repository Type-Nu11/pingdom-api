package com.typenull.pingdom.moderation.api.dto.storage;

import java.util.List;

public record AdminS3OrphanObjectReportResponse(
        String prefix,
        int scanLimit,
        boolean dryRun,
        boolean truncated,
        long dbKeyCount,
        long s3ObjectCount,
        long orphanObjectCount,
        List<String> orphanKeys
) {
}
