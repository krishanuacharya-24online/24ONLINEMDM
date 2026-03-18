package com.e24online.mdm.domain;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;

@Setter
@Getter
@Table("device_installed_application")
public class DeviceInstalledApplication {

    @Id
    private Long id;

    @Column("device_posture_payload_id")
    private Long devicePosturePayloadId;

    @Column("device_trust_profile_id")
    private Long deviceTrustProfileId;

    @Column("capture_time")
    private OffsetDateTime captureTime;

    @Column("app_name")
    private String appName;

    @Column("publisher")
    private String publisher;

    @Column("package_id")
    private String packageId;

    @Column("app_os_type")
    private String appOsType;

    @Column("app_version")
    private String appVersion;

    @Column("latest_available_version")
    private String latestAvailableVersion;

    @Column("is_system_app")
    private Boolean systemApp;

    @Column("install_source")
    private String installSource;

    @Column("status")
    private String status;

    @Column("created_at")
    private OffsetDateTime createdAt;

    @Column("created_by")
    private String createdBy;

}

