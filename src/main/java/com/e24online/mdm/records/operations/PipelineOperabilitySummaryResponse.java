package com.e24online.mdm.records.operations;

import java.time.OffsetDateTime;

public record PipelineOperabilitySummaryResponse(
        OffsetDateTime checkedAt,
        long inFlightPayloads,
        long receivedPayloads,
        long queuedPayloads,
        long validatedPayloads,
        long failedPayloads,
        long failedLast24Hours,
        long queueFailuresLast7Days,
        long evaluationFailuresLast7Days,
        OffsetDateTime oldestInFlightReceivedAt,
        Long oldestInFlightAgeMinutes
) {
}
