package com.e24online.mdm.domain;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;

@Setter
@Getter
@Table("application_catalog")
public class ApplicationCatalogEntry {

    @Id
    private Long id;

    @Column("os_type")
    private String osType;

    @Column("package_id")
    private String packageId;

    @Column("app_name")
    private String appName;

    @Column("publisher")
    private String publisher;

    @Column("created_at")
    private OffsetDateTime createdAt;

    @Column("modified_at")
    private OffsetDateTime modifiedAt;

    @Column("is_deleted")
    private boolean deleted;

}
