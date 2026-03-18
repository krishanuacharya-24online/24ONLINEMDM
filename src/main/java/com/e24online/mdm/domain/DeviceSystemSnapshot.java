package com.e24online.mdm.domain;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;

@Setter
@Getter
@Table("device_system_snapshot")
public class DeviceSystemSnapshot {

    @Id
    private Long id;

    @Column("device_posture_payload_id")
    private Long devicePosturePayloadId;

    @Column("device_trust_profile_id")
    private Long deviceTrustProfileId;

    @Column("capture_time")
    private OffsetDateTime captureTime;

    @Column("device_type")
    private String deviceType;

    @Column("os_type")
    private String osType;

    @Column("os_name")
    private String osName;

    @Column("os_cycle")
    private String osCycle;

    @Column("os_release_lifecycle_master_id")
    private Long osReleaseLifecycleMasterId;

    @Column("os_version")
    private String osVersion;

    @Column("time_zone")
    private String timeZone;

    @Column("kernel_version")
    private String kernelVersion;

    @Column("api_level")
    private Integer apiLevel;

    @Column("os_build_number")
    private String osBuildNumber;

    @Column("manufacturer")
    private String manufacturer;

    @Column("root_detected")
    private Boolean rootDetected;

    @Column("running_on_emulator")
    private Boolean runningOnEmulator;

    @Column("usb_debugging_status")
    private Boolean usbDebuggingStatus;

    @Column("is_latest")
    private Boolean latest;

}
