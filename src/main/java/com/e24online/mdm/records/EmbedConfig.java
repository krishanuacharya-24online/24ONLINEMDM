package com.e24online.mdm.records;

public record EmbedConfig(
        boolean enabled,
        String iframeUrl,
        String iframeSandbox,
        boolean guestTokenEnabled,
        String message,
        String supersetDomain,
        String resourceId,
        String embeddedDashboardId
) {
}
