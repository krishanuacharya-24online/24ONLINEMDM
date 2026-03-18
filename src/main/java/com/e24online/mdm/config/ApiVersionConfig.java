package com.e24online.mdm.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Centralized configuration for API versioning.
 * Provides utility methods to build versioned API paths.
 */
@Getter
@Component
public class ApiVersionConfig {

    /**
     * API version prefix for runtime use (e.g., "/v1").
     * Initialized from application.yaml property: api.version.prefix
     * -- GETTER --
     *  Get the API version prefix (e.g., "/v1").
     *
     */
    private final String prefix;

    public ApiVersionConfig(@Value("${api.version.prefix:v1}") String apiVersionPrefix) {
        this.prefix = normalizePrefix(apiVersionPrefix);
    }

    /**
     * Build a versioned API path.
     * @param path the API path without version (e.g., "/admin/users")
     * @return versioned path (e.g., "/v1/admin/users")
     */
    public String path(String path) {
        if (path == null || path.isEmpty()) {
            return prefix;
        }
        // If already versioned, return as-is
        if (path.startsWith(prefix)) {
            return path;
        }
        return prefix + (path.startsWith("/") ? path : "/" + path);
    }

    /**
     * Check if a path starts with the current API version prefix.
     * @param path the path to check
     * @return true if path starts with version prefix
     */
    public boolean isVersionedPath(String path) {
        return path != null && path.startsWith(prefix);
    }

    /**
     * Normalize the prefix to ensure it starts with "/" but doesn't end with "/".
     */
    private static String normalizePrefix(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return "/v1";
        }
        String normalized = prefix.trim();
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        return normalized;
    }
}
