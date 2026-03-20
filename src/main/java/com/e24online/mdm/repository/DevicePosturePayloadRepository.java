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
            SELECT *
            FROM device_posture_payload
            WHERE (CAST(:tenantId AS varchar) IS NULL OR tenant_id = :tenantId OR (tenant_id IS NULL AND :tenantId IS NULL))
              AND (CAST(:deviceExternalId AS varchar) IS NULL OR device_external_id = :deviceExternalId)
              AND (CAST(:processStatus AS varchar) IS NULL OR process_status = :processStatus)
            ORDER BY received_at DESC, id DESC
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
            SELECT *
            FROM device_posture_payload
            WHERE tenant_id IS NOT DISTINCT FROM :tenantId
              AND device_external_id = :deviceExternalId
            ORDER BY received_at DESC, id DESC
            LIMIT :limit OFFSET :offset
            """)
    List<DevicePosturePayload> findByDevice(
            @Param("tenantId") String tenantId,
            @Param("deviceExternalId") String deviceExternalId,
            @Param("limit") int limit,
            @Param("offset") long offset
    );

    @Query("""
            SELECT *
            FROM device_posture_payload
            WHERE tenant_id IS NOT DISTINCT FROM :tenantId
              AND device_external_id = :deviceExternalId
            ORDER BY received_at DESC, id DESC
            LIMIT 1
            """)
    Optional<DevicePosturePayload> findLatestByDevice(
            @Param("tenantId") String tenantId,
            @Param("deviceExternalId") String deviceExternalId
    );

    @Query("""
            SELECT *
            FROM device_posture_payload
            WHERE tenant_id IS NOT DISTINCT FROM :tenantId
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
            SELECT *
            FROM device_posture_payload
            WHERE id = :id
              AND tenant_id IS NOT DISTINCT FROM :tenantId
            LIMIT 1
            """)
    Optional<DevicePosturePayload> findByIdAndTenant(
            @Param("id") Long id,
            @Param("tenantId") String tenantId
    );

    @Query("""
            SELECT COUNT(*)
            FROM device_posture_payload
            WHERE tenant_id IS NOT DISTINCT FROM :tenantId
              AND received_at >= :fromInclusive
              AND received_at < :toExclusive
            """)
    long countReceivedInWindow(
            @Param("tenantId") String tenantId,
            @Param("fromInclusive") OffsetDateTime fromInclusive,
            @Param("toExclusive") OffsetDateTime toExclusive
    );
}
