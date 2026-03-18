package com.e24online.mdm.repository;

import com.e24online.mdm.domain.RuleRemediationMapping;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;

public interface RuleRemediationMappingRepository extends CrudRepository<RuleRemediationMapping, Long> {

    @Query("""
            SELECT * FROM rule_remediation_mapping
            WHERE is_deleted = false
              AND (CAST(:sourceType AS TEXT) IS NULL OR source_type = CAST(:sourceType AS TEXT))
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
              source_type, rank_order
            LIMIT :limit OFFSET :offset
            """)
    List<RuleRemediationMapping> findPaged(
            @Param("tenantId") String tenantId,
            @Param("sourceType") String sourceType,
            @Param("limit") int limit,
            @Param("offset") long offset
    );

    @Query("""
            SELECT COUNT(*) FROM rule_remediation_mapping
            WHERE is_deleted = false
              AND (CAST(:sourceType AS TEXT) IS NULL OR source_type = CAST(:sourceType AS TEXT))
              AND (
                (CAST(:tenantId AS TEXT) IS NULL AND tenant_id IS NULL)
                OR (
                  CAST(:tenantId AS TEXT) IS NOT NULL
                  AND (tenant_id IS NULL OR LOWER(tenant_id) = LOWER(CAST(:tenantId AS TEXT)))
                )
              )
            """)
    long countActive(@Param("tenantId") String tenantId, @Param("sourceType") String sourceType);

    @Query("""
            SELECT * FROM rule_remediation_mapping
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
              source_type, rank_order, id
            """)
    List<RuleRemediationMapping> findActiveForEvaluation(
            @Param("tenantId") String tenantId,
            @Param("asOf") OffsetDateTime asOf
    );
}
