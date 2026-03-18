package com.e24online.mdm.config;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class JacksonConfigTest {

    @Test
    void objectMapperBean_isCreatedAndUsable() throws Exception {
        JacksonConfig config = new JacksonConfig();
        ObjectMapper mapper = config.objectMapper();

        assertNotNull(mapper);
        String json = mapper.writeValueAsString(Map.of("status", "ok", "count", 2));
        assertEquals("ok", mapper.readTree(json).get("status").asText());
        assertEquals(2, mapper.readTree(json).get("count").asInt());
    }
}
