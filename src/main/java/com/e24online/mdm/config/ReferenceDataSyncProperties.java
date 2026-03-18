package com.e24online.mdm.config;

import com.e24online.mdm.web.dto.AppCatalog;
import com.e24online.mdm.web.dto.OsLifecycle;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Component
@ConfigurationProperties(prefix = "mdm.reference-sync")
public class ReferenceDataSyncProperties {
    @Setter
    private boolean enabled = true;
    @Setter
    private String dailyCron = "0 10 2 * * *";
    @Setter
    private String zone = "Asia/Kolkata";
    @Setter
    private boolean runOnStartup = false;
    @Setter
    private String actor = "system-seed";

    private final OsLifecycle osLifecycle = new OsLifecycle();
    private final AppCatalog appCatalog = new AppCatalog();
}