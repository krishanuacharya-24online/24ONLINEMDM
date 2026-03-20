package com.e24online.mdm.records.operations;

import java.time.LocalDate;

public record PipelineDailyTrendResponse(
        LocalDate bucketDate,
        long ingestSuccessCount,
        long queueSuccessCount,
        long queueFailureCount,
        long evaluationSuccessCount,
        long evaluationFailureCount,
        long failedPayloadCount
) {
}
