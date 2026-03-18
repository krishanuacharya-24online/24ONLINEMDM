package com.e24online.mdm.repository;

import com.e24online.mdm.domain.TrustScorePolicy;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;

public interface TrustScorePolicyRepository extends CrudRepository<TrustScorePolicy, Long> {

    @Query("""
            SELECT *
            FROM trust_score_policy
            WHERE is_deleted = false
              AND (CAST(:status AS varchar) IS NULL OR status = :status)
              AND (
                    (CAST(:tenantId AS varchar) IS NULL AND tenant_id IS NULL)
                 OR (CAST(:tenantId AS varchar) IS NOT NULL AND (tenant_id IS NULL OR tenant_id = :tenantId))
              )
            ORDER BY
              CASE WHEN tenant_id = :tenantId THEN 0 ELSE 1 END,
              source_type,
              signal_key,
              id
            LIMIT :limit OFFSET :offset
            """)
    List<TrustScorePolicy> findPaged(
            @Param("tenantId") String tenantId,
            @Param("status") String status,
            @Param("limit") int limit,
            @Param("offset") long offset
    );

    @Query("""
            SELECT COUNT(*)
            FROM trust_score_policy
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
            FROM trust_score_policy
            WHERE is_deleted = false
              AND status = 'ACTIVE'
              AND effective_from <= :asOf
              AND (effective_to IS NULL OR effective_to > :asOf)
              AND (
                    (CAST(:tenantId AS varchar) IS NULL AND tenant_id IS NULL)
                 OR (CAST(:tenantId AS varchar) IS NOT NULL AND (tenant_id IS NULL OR tenant_id = :tenantId))
              )
            ORDER BY
              CASE WHEN tenant_id = :tenantId THEN 0 ELSE 1 END,
              source_type,
              signal_key,
              id
            """)
    List<TrustScorePolicy> findActiveForEvaluation(
            @Param("tenantId") String tenantId,
            @Param("asOf") OffsetDateTime asOf
    );
}