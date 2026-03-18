package com.e24online.mdm.repository;

import com.e24online.mdm.domain.SystemInformationRule;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;

public interface SystemInformationRuleRepository extends CrudRepository<SystemInformationRule, Long> {

    @Query("""
            SELECT *
            FROM system_information_rule
            WHERE is_deleted = false
              AND (CAST(:status AS varchar) IS NULL OR status = :status)
              AND (
                    (CAST(:tenantId AS varchar) IS NULL AND tenant_id IS NULL)
                 OR (CAST(:tenantId AS varchar) IS NOT NULL AND (tenant_id IS NULL OR tenant_id = :tenantId))
              )
            ORDER BY
              CASE WHEN tenant_id = :tenantId THEN 0 ELSE 1 END,
              os_type,
              os_name,
              priority,
              id
            LIMIT :limit OFFSET :offset
            """)
    List<SystemInformationRule> findPaged(
            @Param("tenantId") String tenantId,
            @Param("status") String status,
            @Param("limit") int limit,
            @Param("offset") long offset
    );

    @Query("""
            SELECT COUNT(*)
            FROM system_information_rule
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
            FROM system_information_rule
            WHERE is_deleted = false
              AND status = 'ACTIVE'
              AND effective_from <= :asOf
              AND (effective_to IS NULL OR effective_to > :asOf)
              AND (CAST(:osType AS varchar) IS NULL OR os_type = :osType)
              AND (
                    (CAST(:tenantId AS varchar) IS NULL AND tenant_id IS NULL)
                 OR (CAST(:tenantId AS varchar) IS NOT NULL AND (tenant_id IS NULL OR tenant_id = :tenantId))
              )
            ORDER BY
              CASE WHEN tenant_id = :tenantId THEN 0 ELSE 1 END,
              priority,
              id
            """)
    List<SystemInformationRule> findActiveForEvaluation(
            @Param("tenantId") String tenantId,
            @Param("osType") String osType,
            @Param("asOf") OffsetDateTime asOf
    );
}