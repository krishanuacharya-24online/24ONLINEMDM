package com.e24online.mdm.repository;

import com.e24online.mdm.domain.PostureEvaluationRemediation;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface PostureEvaluationRemediationRepository extends CrudRepository<PostureEvaluationRemediation, Long> {

    @Query("""
            SELECT COUNT(*) FROM posture_evaluation_remediation
            WHERE remediation_status IN ('PROPOSED', 'DELIVERED', 'USER_ACKNOWLEDGED', 'STILL_OPEN')
            """)
    long countOpenRemediations();

    @Modifying
    @Query("""
            UPDATE posture_evaluation_remediation
               SET remediation_status = 'DELIVERED'
             WHERE posture_evaluation_run_id = :runId
               AND remediation_status = 'PROPOSED'
            """)
    int markDeliveredByRunId(@Param("runId") Long runId);

    @Modifying
    @Query("""
            UPDATE posture_evaluation_remediation
               SET remediation_status = 'USER_ACKNOWLEDGED',
                   completed_at = COALESCE(:completedAt, completed_at)
             WHERE posture_evaluation_run_id = :runId
               AND remediation_status IN ('PROPOSED', 'DELIVERED')
            """)
    int markAcknowledgedByRunId(
            @Param("runId") Long runId,
            @Param("completedAt") OffsetDateTime completedAt
    );

    @Query("""
            SELECT * FROM posture_evaluation_remediation
            WHERE posture_evaluation_run_id = :runId
            ORDER BY created_at ASC
            """)
    List<PostureEvaluationRemediation> findByRunId(@Param("runId") Long runId);

    @Query("""
            SELECT * FROM posture_evaluation_remediation
            WHERE id = :id
              AND posture_evaluation_run_id = :runId
            LIMIT 1
            """)
    Optional<PostureEvaluationRemediation> findByIdAndRunId(
            @Param("id") Long id,
            @Param("runId") Long runId
    );
}
