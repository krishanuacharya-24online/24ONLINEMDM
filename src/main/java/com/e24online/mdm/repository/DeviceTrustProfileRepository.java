package com.e24online.mdm.repository;

import com.e24online.mdm.domain.DeviceTrustProfile;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DeviceTrustProfileRepository extends CrudRepository<DeviceTrustProfile, Long> {

    @Query("""
            SELECT COUNT(*) FROM device_trust_profile
            WHERE is_deleted = false
            """)
    long countActive();

    @Query("""
            SELECT COUNT(*) FROM device_trust_profile
            WHERE is_deleted = false
              AND score_band = 'TRUSTED'
            """)
    long countTrusted();

    @Query("""
            SELECT COUNT(*) FROM device_trust_profile
            WHERE is_deleted = false
              AND score_band IN ('HIGH_RISK', 'CRITICAL')
            """)
    long countHighRisk();

    @Query("""
        SELECT *
        FROM device_trust_profile dtp
        WHERE dtp.is_deleted = false
          AND (CAST(:tenantId AS varchar) IS NULL
               OR COALESCE(dtp.tenant_id, '') = COALESCE(CAST(:tenantId AS varchar), ''))
          AND (CAST(:deviceExternalId AS varchar) IS NULL
               OR dtp.device_external_id = CAST(:deviceExternalId AS varchar))
          AND (CAST(:osType AS varchar) IS NULL
               OR dtp.os_type = CAST(:osType AS varchar))
          AND (CAST(:osName AS varchar) IS NULL
               OR dtp.os_name = CAST(:osName AS varchar))
          AND (CAST(:scoreBand AS varchar) IS NULL
               OR dtp.score_band = CAST(:scoreBand AS varchar))
          AND (CAST(:ownerUserId AS bigint) IS NULL
               OR EXISTS (
                    SELECT 1
                    FROM device_enrollment de
                    WHERE COALESCE(de.tenant_id, '') = COALESCE(dtp.tenant_id, '')
                      AND de.enrollment_no = dtp.device_external_id
                      AND de.owner_user_id = CAST(:ownerUserId AS bigint)
                      AND de.status = 'ACTIVE'
               ))
        ORDER BY dtp.current_score DESC
        LIMIT :limit OFFSET :offset
        """)
    List<DeviceTrustProfile> findPaged(
            @Param("tenantId") String tenantId,
            @Param("deviceExternalId") String deviceExternalId,
            @Param("osType") String osType,
            @Param("osName") String osName,
            @Param("scoreBand") String scoreBand,
            @Param("ownerUserId") Long ownerUserId,
            @Param("limit") int limit,
            @Param("offset") long offset
    );

    @Query("""
            SELECT * FROM device_trust_profile
            WHERE is_deleted = false
              AND COALESCE(tenant_id, '') = COALESCE(:tenantId, '')
              AND device_external_id = :deviceExternalId
            ORDER BY COALESCE(last_recalculated_at, created_at) DESC, id DESC
            LIMIT 1
            """)
    Optional<DeviceTrustProfile> findActiveByTenantAndDevice(
            @Param("tenantId") String tenantId,
            @Param("deviceExternalId") String deviceExternalId
    );

    @Query("""
            SELECT * FROM device_trust_profile
            WHERE id = :id
              AND is_deleted = false
              AND COALESCE(tenant_id, '') = COALESCE(:tenantId, '')
            LIMIT 1
            """)
    Optional<DeviceTrustProfile> findByIdAndTenant(
            @Param("id") Long id,
            @Param("tenantId") String tenantId
    );
}
