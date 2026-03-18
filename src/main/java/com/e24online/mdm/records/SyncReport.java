package com.e24online.mdm.records;

import java.time.OffsetDateTime;
import java.util.List;

public record SyncReport(
        String trigger,
        OffsetDateTime startedAt,
        OffsetDateTime finishedAt,
        int lifecycleUpserts,
        int appCatalogUpserts,
        int iosEnrichedRows,
        List<String> errors
) {
    public boolean success() {
        return errors == null || errors.isEmpty();
    }
}
