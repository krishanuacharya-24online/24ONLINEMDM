package com.e24online.mdm.service;

import com.e24online.mdm.config.SupersetReportingProperties;
import com.e24online.mdm.records.EmbedConfig;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SupersetReportingServiceTest {

    @Test
    void embedConfig_disabledWhenFeatureIsOff() {
        SupersetReportingProperties properties = new SupersetReportingProperties();
        properties.setEnabled(false);

        SupersetReportingService service = new SupersetReportingService(properties, WebClient.builder(), new ObjectMapper());
        EmbedConfig config = service.embedConfig();

        assertFalse(config.enabled());
        assertNull(config.iframeUrl());
    }

    @Test
    void embedConfig_returnsIframeSettingsWhenConfigured() {
        SupersetReportingProperties properties = new SupersetReportingProperties();
        properties.setEnabled(true);
        properties.setBaseUrl("http://localhost:8088/");
        properties.setDashboardPath("/superset/dashboard/2/?standalone=1");
        properties.setIframeSandbox("allow-same-origin");

        SupersetReportingService service = new SupersetReportingService(properties, WebClient.builder(), new ObjectMapper());
        EmbedConfig config = service.embedConfig();

        assertTrue(config.enabled());
        assertEquals("http://localhost:8088/superset/dashboard/2/?standalone=1", config.iframeUrl());
        assertEquals("allow-same-origin", config.iframeSandbox());
    }
}
