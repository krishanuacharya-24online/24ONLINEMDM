package com.e24online.mdm.repository;

import com.e24online.mdm.domain.PostureEvaluationRemediation;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PostureEvaluationRemediationRepository extends CrudRepository<PostureEvaluationRemediation, Long> {

    @Query("""
            SELECT COUNT(*) FROM posture_evaluation_remediation
            WHERE remediation_status IN ('PENDING', 'SENT')
            """)
    long countOpenRemediations();

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
