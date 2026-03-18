package com.e24online.mdm.repository;

import com.e24online.mdm.domain.TrustScoreDecisionPolicy;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface TrustScoreDecisionPolicyRepository extends CrudRepository<TrustScoreDecisionPolicy, Long> {

    @Query("""
            SELECT *
            FROM trust_score_decision_policy
            WHERE is_deleted = false
              AND (CAST(:status AS varchar) IS NULL OR status = :status)
              AND (
                    (CAST(:tenantId AS varchar) IS NULL AND tenant_id IS NULL)
                 OR (CAST(:tenantId AS varchar) IS NOT NULL AND (tenant_id IS NULL OR tenant_id = :tenantId))
              )
            ORDER BY
              CASE WHEN tenant_id = :tenantId THEN 0 ELSE 1 END,
              score_min,
              score_max,
              id
            LIMIT :limit OFFSET :offset
            """)
    List<TrustScoreDecisionPolicy> findPaged(
            @Param("tenantId") String tenantId,
            @Param("status") String status,
            @Param("limit") int limit,
            @Param("offset") long offset
    );

    @Query("""
            SELECT COUNT(*)
            FROM trust_score_decision_policy
            WHERE is_deleted = false
              AND (CAST(:status AS varchar) IS NULL OR status = :status)
              AND (
                    (CAST(:tenantId AS varchar) IS NULL AND tenant_id IS NULL)
                 OR (CAST(:tenantId AS varchar) IS NOT NULL AND (tenant_id IS NULL OR tenant_id = :tenantId))
              )
            """)
    long countActive(
            @Param("tenantId") String tenantId,
            @Param("status") String status
    );

    @Query("""
            SELECT *
            FROM trust_score_decision_policy
            WHERE is_deleted = false
              AND status = 'ACTIVE'
              AND score_min <= :score
              AND score_max >= :score
              AND effective_from <= :asOf
              AND (effective_to IS NULL OR effective_to > :asOf)
              AND (
                    (CAST(:tenantId AS varchar) IS NULL AND tenant_id IS NULL)
                 OR (CAST(:tenantId AS varchar) IS NOT NULL AND (tenant_id IS NULL OR tenant_id = :tenantId))
              )
            ORDER BY
              CASE WHEN tenant_id = :tenantId THEN 0 ELSE 1 END,
              score_min DESC,
              score_max ASC,
              id ASC
            LIMIT 1
            """)
    Optional<TrustScoreDecisionPolicy> findActivePolicyForScore(
            @Param("tenantId") String tenantId,
            @Param("score") int score,
            @Param("asOf") OffsetDateTime asOf
    );
}