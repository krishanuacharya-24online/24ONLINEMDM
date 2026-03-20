package com.e24online.mdm.records.operations;

import java.time.OffsetDateTime;
import java.util.List;

public record QueueHealthSummaryResponse(
        OffsetDateTime checkedAt,
        String overallStatus,
        long totalReadyMessages,
        long totalDeadLetterMessages,
        List<QueueHealthEntryResponse> queues
) {
}
