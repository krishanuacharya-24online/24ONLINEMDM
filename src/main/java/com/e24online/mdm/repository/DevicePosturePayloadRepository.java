package com.e24online.mdm.repository;

import com.e24online.mdm.domain.DevicePosturePayload;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface DevicePosturePayloadRepository extends CrudRepository<DevicePosturePayload, Long> {

    @Query("""
            SELECT * FROM device_posture_payload
            WHERE (:tenantId IS NULL OR COALESCE(tenant_id, '') = COALESCE(:tenantId, ''))
              AND (:deviceExternalId IS NULL OR device_external_id = :deviceExternalId)
              AND (:processStatus IS NULL OR process_status = :processStatus)
            ORDER BY received_at DESC
            LIMIT :limit OFFSET :offset
            """)
    List<DevicePosturePayload> findPaged(
            @Param("tenantId") String tenantId,
            @Param("deviceExternalId") String deviceExternalId,
            @Param("processStatus") String processStatus,
            @Param("limit") int limit,
            @Param("offset") long offset
    );

    @Query("""
            SELECT * FROM device_posture_payload
            WHERE COALESCE(tenant_id, '') = COALESCE(:tenantId, '')
              AND device_external_id = :deviceExternalId
            ORDER BY received_at DESC
            LIMIT :limit OFFSET :offset
            """)
    List<DevicePosturePayload> findByDevice(
            @Param("tenantId") String tenantId,
            @Param("deviceExternalId") String deviceExternalId,
            @Param("limit") int limit,
            @Param("offset") long offset
    );

    @Query("""
            SELECT * FROM device_posture_payload
            WHERE COALESCE(tenant_id, '') = COALESCE(:tenantId, '')
              AND device_external_id = :deviceExternalId
            ORDER BY received_at DESC, id DESC
            LIMIT 1
            """)
    Optional<DevicePosturePayload> findLatestByDevice(
            @Param("tenantId") String tenantId,
            @Param("deviceExternalId") String deviceExternalId
    );

    @Query("""
            SELECT * FROM device_posture_payload
            WHERE COALESCE(tenant_id, '') = COALESCE(:tenantId, '')
              AND device_external_id = :deviceExternalId
              AND idempotency_key = :idempotencyKey
            ORDER BY id DESC
            LIMIT 1
            """)
    Optional<DevicePosturePayload> findByIdempotencyKey(
            @Param("tenantId") String tenantId,
            @Param("deviceExternalId") String deviceExternalId,
            @Param("idempotencyKey") String idempotencyKey
    );

    @Modifying
    @Query("""
            UPDATE device_posture_payload
               SET process_status = 'QUEUED',
                   process_error = NULL,
                   processed_at = NULL
             WHERE id = :id
               AND process_status IN ('RECEIVED', 'FAILED')
            """)
    int claimPayloadForQueue(@Param("id") Long id);

    @Modifying
    @Query("""
            UPDATE device_posture_payload
               SET process_status = 'FAILED',
                   process_error = :processError,
                   processed_at = :processedAt
             WHERE id = :id
            """)
    int markPayloadFailed(
            @Param("id") Long id,
            @Param("processError") String processError,
            @Param("processedAt") OffsetDateTime processedAt
    );

    @Query("""
            SELECT * FROM device_posture_payload
            WHERE id = :id
              AND COALESCE(tenant_id, '') = COALESCE(:tenantId, '')
            LIMIT 1
            """)
    Optional<DevicePosturePayload> findByIdAndTenant(
            @Param("id") Long id,
            @Param("tenantId") String tenantId
    );
}
