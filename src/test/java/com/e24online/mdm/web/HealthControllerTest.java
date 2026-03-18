package com.e24online.mdm.web;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class HealthControllerTest {

    @Test
    void health_returnsUpStatusServiceNameAndTimestamp() {
        HealthController controller = new HealthController();
        ReflectionTestUtils.setField(controller, "applicationName", "mdm-app");

        Map<String, Object> body = controller.health().block();

        assertNotNull(body);
        assertEquals("UP", body.get("status"));
        assertEquals("mdm-app", body.get("service"));
        assertNotNull(body.get("timestamp"));
        assertEquals(OffsetDateTime.class, body.get("timestamp").getClass());
    }
}

