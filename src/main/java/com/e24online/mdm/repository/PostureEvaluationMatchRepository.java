package com.e24online.mdm.repository;

import com.e24online.mdm.domain.PostureEvaluationMatch;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PostureEvaluationMatchRepository extends CrudRepository<PostureEvaluationMatch, Long> {

    @Query("""
            SELECT * FROM posture_evaluation_match
            WHERE posture_evaluation_run_id = :runId
            ORDER BY created_at ASC
            """)
    List<PostureEvaluationMatch> findByRunId(@Param("runId") Long runId);
}

