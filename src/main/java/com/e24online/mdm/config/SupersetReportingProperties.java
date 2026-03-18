package com.e24online.mdm.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

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

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getDashboardPath() {
        return dashboardPath;
    }

    public void setDashboardPath(String dashboardPath) {
        this.dashboardPath = dashboardPath;
    }

    public boolean isGuestTokenEnabled() {
        return guestTokenEnabled;
    }

    public void setGuestTokenEnabled(boolean guestTokenEnabled) {
        this.guestTokenEnabled = guestTokenEnabled;
    }

    public String getEmbeddedDashboardId() {
        return embeddedDashboardId;
    }

    public void setEmbeddedDashboardId(String embeddedDashboardId) {
        this.embeddedDashboardId = embeddedDashboardId;
    }

    public String getEmbeddedAllowedDomains() {
        return embeddedAllowedDomains;
    }

    public void setEmbeddedAllowedDomains(String embeddedAllowedDomains) {
        this.embeddedAllowedDomains = embeddedAllowedDomains;
    }

    public String getResourceType() {
        return resourceType;
    }

    public void setResourceType(String resourceType) {
        this.resourceType = resourceType;
    }

    public String getResourceId() {
        return resourceId;
    }

    public void setResourceId(String resourceId) {
        this.resourceId = resourceId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getAuthProvider() {
        return authProvider;
    }

    public void setAuthProvider(String authProvider) {
        this.authProvider = authProvider;
    }

    public String getTenantRlsClauseTemplate() {
        return tenantRlsClauseTemplate;
    }

    public void setTenantRlsClauseTemplate(String tenantRlsClauseTemplate) {
        this.tenantRlsClauseTemplate = tenantRlsClauseTemplate;
    }

    public String getIframeSandbox() {
        return iframeSandbox;
    }

    public void setIframeSandbox(String iframeSandbox) {
        this.iframeSandbox = iframeSandbox;
    }
}
