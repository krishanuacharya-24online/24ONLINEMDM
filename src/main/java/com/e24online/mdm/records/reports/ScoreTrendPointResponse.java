package com.e24online.mdm.records.reports;

import java.time.LocalDate;

public record ScoreTrendPointResponse(
        LocalDate bucketDate,
        long evaluationCount,
        long distinctDevices,
        double averageTrustScore,
        long allowCount,
        long notifyCount,
        long quarantineCount,
        long blockCount
) {
}
