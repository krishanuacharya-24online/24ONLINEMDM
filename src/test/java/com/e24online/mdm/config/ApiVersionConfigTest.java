package com.e24online.mdm.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiVersionConfigTest {

    @Test
    void constructorNormalizesPrefix() {
        ApiVersionConfig config = new ApiVersionConfig("v2");
        assertEquals("/v2", config.getPrefix());
    }

    @Test
    void constructorUsesDefaultWhenBlank() {
        ApiVersionConfig config = new ApiVersionConfig("   ");
        assertEquals("/v1", config.getPrefix());
    }

    @Test
    void constructorUsesDefaultWhenNull() {
        ApiVersionConfig config = new ApiVersionConfig(null);
        assertEquals("/v1", config.getPrefix());
    }

    @Test
    void pathAndVersionChecksBehaveCorrectly() {
        ApiVersionConfig config = new ApiVersionConfig("/v5");

        assertEquals("/v5", config.path(null));
        assertEquals("/v5", config.path(""));
        assertEquals("/v5/admin/users", config.path("/admin/users"));
        assertEquals("/v5/admin/users", config.path("admin/users"));
        assertEquals("/v5/admin/users", config.path("/v5/admin/users"));
        assertFalse(config.isVersionedPath(null));
        assertTrue(config.isVersionedPath("/v5/health"));
        assertFalse(config.isVersionedPath("/v4/health"));
    }
}
