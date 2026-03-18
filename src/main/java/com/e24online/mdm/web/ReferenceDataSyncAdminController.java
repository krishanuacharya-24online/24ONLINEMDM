package com.e24online.mdm.web;

import com.e24online.mdm.records.SyncReport;
import com.e24online.mdm.service.BlockingDb;
import com.e24online.mdm.service.ReferenceDataSyncService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("${api.version.prefix:v1}/admin/reference-sync")
@PreAuthorize("hasRole('PRODUCT_ADMIN')")
public class ReferenceDataSyncAdminController {

    private final ReferenceDataSyncService syncService;
    private final BlockingDb blockingDb;

    public ReferenceDataSyncAdminController(ReferenceDataSyncService syncService, BlockingDb blockingDb) {
        this.syncService = syncService;
        this.blockingDb = blockingDb;
    }

    @PostMapping("/run")
    public Mono<Map<String, Object>> runNow(
            @RequestParam(name = "trigger", defaultValue = "manual-api") String trigger
    ) {
        return blockingDb.mono(() -> {
            SyncReport report = syncService.syncAll(trigger);
            return toResponse(report);
        });
    }

    @GetMapping("/status")
    public Mono<Map<String, Object>> status() {
        return blockingDb.mono(() -> toResponse(syncService.latestReport().orElse(null)));
    }

    private Map<String, Object> toResponse(SyncReport report) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("has_report", report != null);
        response.put("trigger", report == null ? null : report.trigger());
        response.put("started_at", report == null ? null : report.startedAt());
        response.put("finished_at", report == null ? null : report.finishedAt());
        response.put("last_sync_at", report == null ? null : report.finishedAt());
        response.put("lifecycle_upserts", report == null ? 0 : report.lifecycleUpserts());
        response.put("app_catalog_upserts", report == null ? 0 : report.appCatalogUpserts());
        response.put("ios_enriched_rows", report == null ? 0 : report.iosEnrichedRows());
        response.put("success", report != null && report.success());
        response.put("errors", report == null ? java.util.List.of() : report.errors());
        return response;
    }
}
