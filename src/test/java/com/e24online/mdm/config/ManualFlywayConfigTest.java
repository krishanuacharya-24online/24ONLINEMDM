package com.e24online.mdm.config;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class ManualFlywayConfigTest {

    @Test
    void flywayBean_configuresBaselineAndMigrationLocation() {
        ManualFlywayConfig config = new ManualFlywayConfig();
        DataSource dataSource = mock(DataSource.class);

        Flyway flyway = config.flyway(dataSource);

        assertNotNull(flyway);
        assertEquals(dataSource, flyway.getConfiguration().getDataSource());
        assertTrue(flyway.getConfiguration().isBaselineOnMigrate());
        assertEquals(MigrationVersion.fromVersion("0"), flyway.getConfiguration().getBaselineVersion());
        assertTrue(Arrays.stream(flyway.getConfiguration().getLocations())
                .anyMatch(location -> location.toString().contains("db/migration")));
    }
}

