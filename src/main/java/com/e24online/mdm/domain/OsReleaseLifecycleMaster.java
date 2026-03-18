package com.e24online.mdm.domain;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Setter
@Getter
@Table("os_release_lifecycle_master")
public class OsReleaseLifecycleMaster {

    @Id
    private Long id;

    @Column("platform_code")
    private String platformCode;

    @Column("os_type")
    private String osType;

    @Column("os_name")
    private String osName;

    @Column("cycle")
    private String cycle;

    @Column("released_on")
    private LocalDate releasedOn;

    @Column("eol_on")
    private LocalDate eolOn;

    @Column("eeol_on")
    private LocalDate eeolOn;

    @Column("latest_version")
    private String latestVersion;

    @Column("support_state")
    private String supportState;

    @Column("source_name")
    private String sourceName;

    @Column("source_url")
    private String sourceUrl;

    @Column("notes")
    private String notes;

    @Column("status")
    private String status;

    @Column("is_deleted")
    private boolean deleted;

    @Column("created_at")
    private OffsetDateTime createdAt;

    @Column("created_by")
    private String createdBy;

    @Column("modified_at")
    private OffsetDateTime modifiedAt;

    @Column("modified_by")
    private String modifiedBy;

}

