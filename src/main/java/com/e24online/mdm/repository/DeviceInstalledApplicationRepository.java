package com.e24online.mdm.repository;

import com.e24online.mdm.domain.DeviceInstalledApplication;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DeviceInstalledApplicationRepository extends CrudRepository<DeviceInstalledApplication, Long> {

    @Query("""
            SELECT dia.* FROM device_installed_application dia
            JOIN device_trust_profile dtp
              ON dia.device_trust_profile_id = dtp.id
            WHERE dtp.device_external_id = :deviceExternalId
              AND (CAST(:tenantId AS varchar) IS NULL
                   OR COALESCE(dtp.tenant_id, '') = COALESCE(CAST(:tenantId AS varchar), ''))
            ORDER BY dia.capture_time DESC
            LIMIT :limit OFFSET :offset
            """)
    List<DeviceInstalledApplication> findLatestAppsByDevice(
            @Param("tenantId") String tenantId,
            @Param("deviceExternalId") String deviceExternalId,
            @Param("limit") int limit,
            @Param("offset") long offset
    );

    @Query("""
            SELECT dia.* FROM device_installed_application dia
            JOIN device_trust_profile dtp
              ON dia.device_trust_profile_id = dtp.id
            WHERE dtp.device_external_id = :deviceExternalId
              AND (CAST(:tenantId AS varchar) IS NULL
                   OR COALESCE(dtp.tenant_id, '') = COALESCE(CAST(:tenantId AS varchar), ''))
              AND (CAST(:status AS varchar) IS NULL
                   OR dia.status = CAST(:status AS varchar))
            ORDER BY dia.capture_time DESC
            LIMIT :limit OFFSET :offset
            """)
    List<DeviceInstalledApplication> findAppsByDevice(
            @Param("tenantId") String tenantId,
            @Param("deviceExternalId") String deviceExternalId,
            @Param("status") String status,
            @Param("limit") int limit,
            @Param("offset") long offset
    );

    @Query("""
            SELECT * FROM device_installed_application
            WHERE device_posture_payload_id = :payloadId
            ORDER BY id
            """)
    List<DeviceInstalledApplication> findByPayloadId(@Param("payloadId") Long payloadId);
}
