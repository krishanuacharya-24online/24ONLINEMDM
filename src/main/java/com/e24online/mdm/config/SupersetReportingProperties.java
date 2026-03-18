package com.e24online.mdm.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Setter
@Getter
@Component
@ConfigurationProperties(prefix = "reports.superset")
public class SupersetReportingProperties {

    private boolean enabled = true;
    private String baseUrl = "http://localhost:8088";
    private String dashboardPath = "/superset/dashboard/1/?standalone=1";
    private boolean guestTokenEnabled = false;
    private String embeddedDashboardId;
    private String embeddedAllowedDomains = "http://localhost:8080,http://127.0.0.1:8080";
    private String resourceType = "dashboard";
    private String resourceId;
    private String username;
    private String password;
    private String authProvider = "db";
    private String tenantRlsClauseTemplate;
    private String iframeSandbox = "allow-same-origin allow-scripts allow-forms allow-popups";
}