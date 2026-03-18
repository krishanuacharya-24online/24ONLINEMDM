package com.e24online.mdm.repository;

import com.e24online.mdm.domain.RejectApplication;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;

public interface RejectApplicationRepository extends CrudRepository<RejectApplication, Long> {

    @Query("""
            SELECT * FROM reject_application_list
            WHERE is_deleted = false
              AND (:osType IS NULL OR app_os_type = :osType)
              AND (:status IS NULL OR status = :status)
              AND (
                (:tenantId IS NULL AND tenant_id IS NULL)
                OR (
                  :tenantId IS NOT NULL
                  AND (tenant_id IS NULL OR tenant_id = :tenantId)
                )
              )
            ORDER BY
              CASE
                WHEN :tenantId IS NOT NULL AND tenant_id = :tenantId THEN 0
                ELSE 1
              END,
              app_os_type, app_name
            LIMIT :limit OFFSET :offset
            """)
    List<RejectApplication> findPaged(
            @Param("tenantId") String tenantId,
            @Param("osType") String osType,
            @Param("status") String status,
            @Param("limit") int limit,
            @Param("offset") long offset
    );

    @Query("""
            SELECT COUNT(*) FROM reject_application_list
            WHERE is_deleted = false
              AND (:osType IS NULL OR app_os_type = :osType)
              AND (:status IS NULL OR status = :status)
              AND (
                (:tenantId IS NULL AND tenant_id IS NULL)
                OR (
                  :tenantId IS NOT NULL
                  AND (tenant_id IS NULL OR tenant_id = :tenantId)
                )
              )
            """)
    long countActive(
            @Param("tenantId") String tenantId,
            @Param("osType") String osType,
            @Param("status") String status
    );

    @Query("""
            SELECT * FROM reject_application_list
            WHERE is_deleted = false
              AND status = 'ACTIVE'
              AND app_os_type = :osType
              AND effective_from <= :asOf
              AND (effective_to IS NULL OR effective_to > :asOf)
              AND (
                (:tenantId IS NULL AND tenant_id IS NULL)
                OR (
                  :tenantId IS NOT NULL
                  AND (tenant_id IS NULL OR tenant_id = :tenantId)
                )
              )
            ORDER BY
              CASE
                WHEN :tenantId IS NOT NULL AND tenant_id = :tenantId THEN 0
                ELSE 1
              END,
              app_name, id
            """)
    List<RejectApplication> findActiveForEvaluation(
            @Param("tenantId") String tenantId,
            @Param("osType") String osType,
            @Param("asOf") OffsetDateTime asOf
    );
}
