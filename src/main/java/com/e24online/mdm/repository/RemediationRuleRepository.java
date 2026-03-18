package com.e24online.mdm.repository;

import com.e24online.mdm.domain.RemediationRule;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;

public interface RemediationRuleRepository extends CrudRepository<RemediationRule, Long> {

    @Query("""
            SELECT * FROM remediation_rule
            WHERE is_deleted = false
              AND (CAST(:status AS TEXT) IS NULL OR status = CAST(:status AS TEXT))
              AND (
                (CAST(:tenantId AS TEXT) IS NULL AND tenant_id IS NULL)
                OR (
                  CAST(:tenantId AS TEXT) IS NOT NULL
                  AND (tenant_id IS NULL OR LOWER(tenant_id) = LOWER(CAST(:tenantId AS TEXT)))
                )
              )
            ORDER BY
              CASE
                WHEN CAST(:tenantId AS TEXT) IS NOT NULL AND LOWER(tenant_id) = LOWER(CAST(:tenantId AS TEXT)) THEN 0
                ELSE 1
              END,
              remediation_code
            LIMIT :limit OFFSET :offset
            """)
    List<RemediationRule> findPaged(
            @Param("tenantId") String tenantId,
            @Param("status") String status,
            @Param("limit") int limit,
            @Param("offset") long offset
    );

    @Query("""
            SELECT COUNT(*) FROM remediation_rule
            WHERE is_deleted = false
              AND (CAST(:status AS TEXT) IS NULL OR status = CAST(:status AS TEXT))
              AND (
                (CAST(:tenantId AS TEXT) IS NULL AND tenant_id IS NULL)
                OR (
                  CAST(:tenantId AS TEXT) IS NOT NULL
                  AND (tenant_id IS NULL OR LOWER(tenant_id) = LOWER(CAST(:tenantId AS TEXT)))
                )
              )
            """)
    long countActive(@Param("tenantId") String tenantId, @Param("status") String status);

    @Query("""
            SELECT * FROM remediation_rule
            WHERE is_deleted = false
              AND status = 'ACTIVE'
              AND effective_from <= :asOf
              AND (effective_to IS NULL OR effective_to > :asOf)
              AND (
                (CAST(:tenantId AS TEXT) IS NULL AND tenant_id IS NULL)
                OR (
                  CAST(:tenantId AS TEXT) IS NOT NULL
                  AND (tenant_id IS NULL OR LOWER(tenant_id) = LOWER(CAST(:tenantId AS TEXT)))
                )
              )
            ORDER BY
              CASE
                WHEN CAST(:tenantId AS TEXT) IS NOT NULL AND LOWER(tenant_id) = LOWER(CAST(:tenantId AS TEXT)) THEN 0
                ELSE 1
              END,
              priority, remediation_code, id
            """)
    List<RemediationRule> findActiveForEvaluation(
            @Param("tenantId") String tenantId,
            @Param("asOf") OffsetDateTime asOf
    );
}
