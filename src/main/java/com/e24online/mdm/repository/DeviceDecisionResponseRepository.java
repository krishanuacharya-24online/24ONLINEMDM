package com.e24online.mdm.repository;

import com.e24online.mdm.domain.DeviceDecisionResponse;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DeviceDecisionResponseRepository extends CrudRepository<DeviceDecisionResponse, Long> {

    @Query("""
            SELECT * FROM device_decision_response
            WHERE posture_evaluation_run_id = :runId
            LIMIT 1
            """)
    Optional<DeviceDecisionResponse> findByRunId(@Param("runId") Long runId);

    @Query("""
            SELECT * FROM device_decision_response
            WHERE posture_evaluation_run_id = :runId
              AND COALESCE(tenant_id, '') = COALESCE(:tenantId, '')
            LIMIT 1
            """)
    Optional<DeviceDecisionResponse> findByRunIdAndTenant(
            @Param("runId") Long runId,
            @Param("tenantId") String tenantId
    );

    @Query("""
            SELECT * FROM device_decision_response
            WHERE COALESCE(tenant_id, '') = COALESCE(:tenantId, '')
              AND device_external_id = :deviceExternalId
            ORDER BY created_at DESC
            LIMIT 1
            """)
    Optional<DeviceDecisionResponse> findLatestByDevice(
            @Param("tenantId") String tenantId,
            @Param("deviceExternalId") String deviceExternalId
    );

    @Query("""
            SELECT * FROM device_decision_response
            WHERE COALESCE(tenant_id, '') = COALESCE(:tenantId, '')
              AND device_external_id = :deviceExternalId
            ORDER BY created_at DESC
            LIMIT :limit OFFSET :offset
            """)
    List<DeviceDecisionResponse> findByDevice(
            @Param("tenantId") String tenantId,
            @Param("deviceExternalId") String deviceExternalId,
            @Param("limit") int limit,
            @Param("offset") long offset
    );

    @Query("""
            SELECT * FROM device_decision_response
            WHERE id = :id
              AND COALESCE(tenant_id, '') = COALESCE(:tenantId, '')
            LIMIT 1
            """)
    Optional<DeviceDecisionResponse> findByIdAndTenant(
            @Param("id") Long id,
            @Param("tenantId") String tenantId
    );

    @Query("""
            SELECT ddr.*
            FROM device_decision_response ddr
            JOIN posture_evaluation_run per
              ON per.id = ddr.posture_evaluation_run_id
            WHERE per.device_trust_profile_id = :profileId
            ORDER BY ddr.created_at DESC
            """)
    List<DeviceDecisionResponse> findByDeviceTrustProfileIdOrderByCreatedAtDesc(@Param("profileId") Long profileId);
}
