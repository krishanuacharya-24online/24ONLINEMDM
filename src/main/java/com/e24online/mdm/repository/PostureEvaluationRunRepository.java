package com.e24online.mdm.repository;

import com.e24online.mdm.domain.PostureEvaluationRun;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PostureEvaluationRunRepository extends CrudRepository<PostureEvaluationRun, Long> {

    @Query("""
            SELECT * FROM posture_evaluation_run
            WHERE (:status IS NULL OR evaluation_status = :status)
            ORDER BY evaluated_at DESC
            LIMIT :limit OFFSET :offset
            """)
    List<PostureEvaluationRun> findPaged(
            @Param("status") String status,
            @Param("limit") int limit,
            @Param("offset") long offset
    );

    @Query("""
            SELECT r.* FROM posture_evaluation_run r
            JOIN device_posture_payload p
              ON p.id = r.device_posture_payload_id
            WHERE COALESCE(p.tenant_id, '') = COALESCE(:tenantId, '')
              AND (:status IS NULL OR r.evaluation_status = :status)
            ORDER BY r.evaluated_at DESC
            LIMIT :limit OFFSET :offset
            """)
    List<PostureEvaluationRun> findPagedByTenant(
            @Param("tenantId") String tenantId,
            @Param("status") String status,
            @Param("limit") int limit,
            @Param("offset") long offset
    );

    @Query("""
            SELECT r.* FROM posture_evaluation_run r
            JOIN device_posture_payload p
              ON p.id = r.device_posture_payload_id
            WHERE COALESCE(p.tenant_id, '') = COALESCE(:tenantId, '')
              AND p.device_external_id = :deviceExternalId
            ORDER BY r.evaluated_at DESC, r.id DESC
            LIMIT :limit OFFSET :offset
            """)
    List<PostureEvaluationRun> findByDevice(
            @Param("tenantId") String tenantId,
            @Param("deviceExternalId") String deviceExternalId,
            @Param("limit") int limit,
            @Param("offset") long offset
    );

    @Query("""
            SELECT * FROM posture_evaluation_run
            WHERE device_posture_payload_id = :payloadId
            """)
    List<PostureEvaluationRun> findByPayloadId(@Param("payloadId") Long payloadId);

    @Query("""
            SELECT * FROM posture_evaluation_run
            WHERE device_posture_payload_id = :payloadId
            LIMIT 1
            """)
    Optional<PostureEvaluationRun> findOneByPayloadId(@Param("payloadId") Long payloadId);

    @Query("""
            SELECT r.* FROM posture_evaluation_run r
            JOIN device_posture_payload p
              ON p.id = r.device_posture_payload_id
            WHERE r.id = :runId
              AND COALESCE(p.tenant_id, '') = COALESCE(:tenantId, '')
            LIMIT 1
            """)
    Optional<PostureEvaluationRun> findByIdAndTenant(
            @Param("runId") Long runId,
            @Param("tenantId") String tenantId
    );

    @Query("SELECT * FROM posture_evaluation_run WHERE device_trust_profile_id = :profileId ORDER BY created_at DESC")
    List<PostureEvaluationRun> findByDeviceTrustProfileIdOrderByCreatedAtDesc(@Param("profileId") Long profileId);

    @Query("""
            SELECT r.* FROM posture_evaluation_run r
            WHERE r.device_trust_profile_id = :profileId
            ORDER BY r.evaluated_at DESC, r.id DESC
            LIMIT :limit OFFSET :offset
            """)
    List<PostureEvaluationRun> findByDeviceTrustProfileIdPaged(
            @Param("profileId") Long profileId,
            @Param("limit") int limit,
            @Param("offset") long offset
    );
}
