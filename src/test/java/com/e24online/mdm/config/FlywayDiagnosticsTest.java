package com.e24online.mdm.config;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationInfoService;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FlywayDiagnosticsTest {

    @Mock
    private ObjectProvider<Flyway> flywayProvider;

    @Mock
    private Flyway flyway;

    @Mock
    private MigrationInfoService migrationInfoService;

    @Mock
    private MigrationInfo current;

    @Mock
    private MigrationInfo pendingOne;

    @Mock
    private MigrationInfo pendingTwo;

    @Mock
    private ApplicationArguments args;

    private FlywayDiagnostics diagnostics;

    @BeforeEach
    void setUp() {
        diagnostics = new FlywayDiagnostics(flywayProvider);
    }

    @Test
    void run_whenFlywayBeanMissing_doesNothing() {
        when(flywayProvider.getIfAvailable()).thenReturn(null);

        assertDoesNotThrow(() -> diagnostics.run(args));
        verify(flywayProvider, times(1)).getIfAvailable();
        verify(flyway, never()).info();
    }

    @Test
    void run_whenFlywayPresent_readsCurrentAndPendingMigrations() {
        when(flywayProvider.getIfAvailable()).thenReturn(flyway);
        when(flyway.info()).thenReturn(migrationInfoService);
        when(migrationInfoService.current()).thenReturn(current);
        when(current.getVersion()).thenReturn(MigrationVersion.fromVersion("1"));
        when(migrationInfoService.pending()).thenReturn(new MigrationInfo[]{pendingOne, pendingTwo});
        when(pendingOne.getVersion()).thenReturn(MigrationVersion.fromVersion("2"));
        when(pendingOne.getDescription()).thenReturn("add table");
        when(pendingTwo.getVersion()).thenReturn(MigrationVersion.fromVersion("3"));
        when(pendingTwo.getDescription()).thenReturn("add index");

        assertDoesNotThrow(() -> diagnostics.run(args));

        verify(flyway, times(1)).info();
        verify(migrationInfoService, times(1)).current();
        verify(migrationInfoService, times(2)).pending();
        verify(current, times(1)).getVersion();
        verify(pendingOne, times(1)).getVersion();
        verify(pendingOne, times(1)).getDescription();
        verify(pendingTwo, times(1)).getVersion();
        verify(pendingTwo, times(1)).getDescription();
    }

    @Test
    void run_withNoCurrentMigration_stillReadsPending() {
        when(flywayProvider.getIfAvailable()).thenReturn(flyway);
        when(flyway.info()).thenReturn(migrationInfoService);
        when(migrationInfoService.current()).thenReturn(null);
        when(migrationInfoService.pending()).thenReturn(new MigrationInfo[0]);

        assertDoesNotThrow(() -> diagnostics.run(args));
        verify(migrationInfoService, times(2)).pending();
    }
}

