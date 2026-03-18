package com.e24online.mdm.service;

import com.e24online.mdm.config.ReferenceDataSyncProperties;
import com.e24online.mdm.records.SyncReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class ReferenceDataSyncScheduler {
    private static final Logger log = LoggerFactory.getLogger(ReferenceDataSyncScheduler.class);

    private final ReferenceDataSyncService syncService;
    private final ReferenceDataSyncProperties properties;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public ReferenceDataSyncScheduler(ReferenceDataSyncService syncService,
                                      ReferenceDataSyncProperties properties) {
        this.syncService = syncService;
        this.properties = properties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void runOnStartupIfConfigured() {
        if (!properties.isEnabled() || !properties.isRunOnStartup()) {
            return;
        }
        runSync("startup");
    }

    @Scheduled(cron = "${mdm.reference-sync.daily-cron:0 10 2 * * *}", zone = "${mdm.reference-sync.zone:Asia/Kolkata}")
    public void runDaily() {
        if (!properties.isEnabled()) {
            return;
        }
        runSync("daily-cron");
    }

    private void runSync(String trigger) {
        if (!running.compareAndSet(false, true)) {
            log.warn("Reference sync already running. Skipping trigger={}", trigger);
            return;
        }
        try {
            SyncReport report = syncService.syncAll(trigger);
            if (report.success()) {
                log.info(
                        "Reference sync completed: trigger={} lifecycleUpserts={} appCatalogUpserts={} iosEnriched={} duration={}ms",
                        report.trigger(),
                        report.lifecycleUpserts(),
                        report.appCatalogUpserts(),
                        report.iosEnrichedRows(),
                        Math.max(0, report.finishedAt().toInstant().toEpochMilli() - report.startedAt().toInstant().toEpochMilli())
                );
            } else {
                log.warn(
                        "Reference sync completed with errors: trigger={} errors={}",
                        report.trigger(),
                        report.errors()
                );
            }
        } catch (Exception ex) {
            log.error("Reference sync failed for trigger={}", trigger, ex);
        } finally {
            running.set(false);
        }
    }
}

