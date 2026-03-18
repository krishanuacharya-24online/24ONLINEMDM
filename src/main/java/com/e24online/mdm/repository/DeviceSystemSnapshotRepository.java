package com.e24online.mdm.repository;

import com.e24online.mdm.domain.DeviceSystemSnapshot;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DeviceSystemSnapshotRepository extends CrudRepository<DeviceSystemSnapshot, Long> {

    @Query("""
            SELECT dss.* FROM device_system_snapshot dss
            JOIN device_trust_profile dtp
              ON dss.device_trust_profile_id = dtp.id
            WHERE dtp.device_external_id = :deviceExternalId
              AND COALESCE(dtp.tenant_id, '') = COALESCE(:tenantId, '')
            ORDER BY dss.capture_time DESC
            LIMIT 1
            """)
    Optional<DeviceSystemSnapshot> findLatestByDevice(
            @Param("tenantId") String tenantId,
            @Param("deviceExternalId") String deviceExternalId
    );

    @Query("""
            SELECT dss.* FROM device_system_snapshot dss
            JOIN device_trust_profile dtp
              ON dss.device_trust_profile_id = dtp.id
            WHERE dtp.device_external_id = :deviceExternalId
              AND COALESCE(dtp.tenant_id, '') = COALESCE(:tenantId, '')
            ORDER BY dss.capture_time DESC
            LIMIT :limit OFFSET :offset
            """)
    List<DeviceSystemSnapshot> findByDevice(
            @Param("tenantId") String tenantId,
            @Param("deviceExternalId") String deviceExternalId,
            @Param("limit") int limit,
            @Param("offset") long offset
    );
}

