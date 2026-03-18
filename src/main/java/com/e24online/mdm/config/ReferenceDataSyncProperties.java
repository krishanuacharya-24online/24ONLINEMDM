package com.e24online.mdm.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "mdm.reference-sync")
public class ReferenceDataSyncProperties {

    private boolean enabled = true;
    private String dailyCron = "0 10 2 * * *";
    private String zone = "Asia/Kolkata";
    private boolean runOnStartup = false;
    private String actor = "system-seed";
    private final OsLifecycle osLifecycle = new OsLifecycle();
    private final AppCatalog appCatalog = new AppCatalog();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getDailyCron() {
        return dailyCron;
    }

    public void setDailyCron(String dailyCron) {
        this.dailyCron = dailyCron;
    }

    public String getZone() {
        return zone;
    }

    public void setZone(String zone) {
        this.zone = zone;
    }

    public boolean isRunOnStartup() {
        return runOnStartup;
    }

    public void setRunOnStartup(boolean runOnStartup) {
        this.runOnStartup = runOnStartup;
    }

    public String getActor() {
        return actor;
    }

    public void setActor(String actor) {
        this.actor = actor;
    }

    public OsLifecycle getOsLifecycle() {
        return osLifecycle;
    }

    public AppCatalog getAppCatalog() {
        return appCatalog;
    }

    public static class OsLifecycle {
        private boolean enabled = true;
        private int restoreFromYear = 2015;
        private int requestTimeoutSeconds = 20;
        private int retries = 3;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getRestoreFromYear() {
            return restoreFromYear;
        }

        public void setRestoreFromYear(int restoreFromYear) {
            this.restoreFromYear = restoreFromYear;
        }

        public int getRequestTimeoutSeconds() {
            return requestTimeoutSeconds;
        }

        public void setRequestTimeoutSeconds(int requestTimeoutSeconds) {
            this.requestTimeoutSeconds = requestTimeoutSeconds;
        }

        public int getRetries() {
            return retries;
        }

        public void setRetries(int retries) {
            this.retries = retries;
        }
    }

    public static class AppCatalog {
        private boolean enabled = true;
        private String appMapLocation = "classpath:sync/app_map.json";
        private int requestTimeoutSeconds = 20;
        private int retries = 2;
        private boolean enrichIosFromItunes = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getAppMapLocation() {
            return appMapLocation;
        }

        public void setAppMapLocation(String appMapLocation) {
            this.appMapLocation = appMapLocation;
        }

        public int getRequestTimeoutSeconds() {
            return requestTimeoutSeconds;
        }

        public void setRequestTimeoutSeconds(int requestTimeoutSeconds) {
            this.requestTimeoutSeconds = requestTimeoutSeconds;
        }

        public int getRetries() {
            return retries;
        }

        public void setRetries(int retries) {
            this.retries = retries;
        }

        public boolean isEnrichIosFromItunes() {
            return enrichIosFromItunes;
        }

        public void setEnrichIosFromItunes(boolean enrichIosFromItunes) {
            this.enrichIosFromItunes = enrichIosFromItunes;
        }
    }
}

