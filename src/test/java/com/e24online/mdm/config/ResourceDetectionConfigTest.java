package com.e24online.mdm.config;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ResourceDetectionConfigTest {

    @Test
    void logSystemResources_runsWithInjectedValues() {
        ResourceDetectionConfig config = new ResourceDetectionConfig();
        ReflectionTestUtils.setField(config, "hikariMaxPoolSize", 120);
        ReflectionTestUtils.setField(config, "rateLimitRps", 750);

        assertDoesNotThrow(config::logSystemResources);
        assertEquals(120, ReflectionTestUtils.getField(config, "hikariMaxPoolSize"));
        assertEquals(750, ReflectionTestUtils.getField(config, "rateLimitRps"));
    }
}

