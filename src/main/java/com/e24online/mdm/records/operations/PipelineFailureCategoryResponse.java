package com.e24online.mdm.records.operations;

import java.time.OffsetDateTime;

public record PipelineFailureCategoryResponse(
        String categoryKey,
        long failureCount,
        OffsetDateTime latestFailureAt,
        String sampleProcessError
) {
}
