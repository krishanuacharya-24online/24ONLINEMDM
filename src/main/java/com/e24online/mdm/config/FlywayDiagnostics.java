package com.e24online.mdm.config;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class FlywayDiagnostics implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(FlywayDiagnostics.class);

    private final ObjectProvider<Flyway> flywayProvider;

    public FlywayDiagnostics(ObjectProvider<Flyway> flywayProvider) {
        this.flywayProvider = flywayProvider;
    }

    @Override
    public void run(@NonNull ApplicationArguments args) {
        Flyway flyway = flywayProvider.getIfAvailable();
        if (flyway == null) {
            log.warn("Flyway bean not present; migrations will not run.");
            return;
        }
        var info = flyway.info();
        MigrationInfo current = info.current();
        log.info("Flyway current: {}", current != null ? current.getVersion() : "<none>");
        log.info("Flyway pending migrations: {}", info.pending().length);
        for (MigrationInfo pending : info.pending()) {
            log.info("Pending: {} {}", pending.getVersion(), pending.getDescription());
        }
    }
}

