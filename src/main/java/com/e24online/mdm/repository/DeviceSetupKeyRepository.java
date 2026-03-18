package com.e24online.mdm.repository;

import com.e24online.mdm.domain.DeviceSetupKey;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DeviceSetupKeyRepository extends CrudRepository<DeviceSetupKey, Long> {

    @Query("""
            SELECT * FROM device_setup_key
            WHERE COALESCE(tenant_id, '') = COALESCE(:tenantId, '')
              AND (:status IS NULL OR status = :status)
            ORDER BY created_at DESC, id DESC
            LIMIT :limit OFFSET :offset
            """)
    List<DeviceSetupKey> findPagedByTenant(
            @Param("tenantId") String tenantId,
            @Param("status") String status,
            @Param("limit") int limit,
            @Param("offset") long offset
    );

    @Query("""
            SELECT * FROM device_setup_key
            WHERE id = :id
              AND COALESCE(tenant_id, '') = COALESCE(:tenantId, '')
            LIMIT 1
            """)
    Optional<DeviceSetupKey> findByIdAndTenant(
            @Param("id") Long id,
            @Param("tenantId") String tenantId
    );

    @Query("""
            SELECT * FROM device_setup_key
            WHERE COALESCE(tenant_id, '') = COALESCE(:tenantId, '')
              AND key_hash = :keyHash
              AND status = 'ACTIVE'
            LIMIT 1
            """)
    Optional<DeviceSetupKey> findActiveByTenantAndHash(
            @Param("tenantId") String tenantId,
            @Param("keyHash") String keyHash
    );

    @Query("""
            SELECT * FROM device_setup_key
            WHERE key_hash = :keyHash
              AND status = 'ACTIVE'
            ORDER BY id DESC
            LIMIT 2
            """)
    List<DeviceSetupKey> findActiveByHash(@Param("keyHash") String keyHash);
}
