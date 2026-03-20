package com.e24online.mdm.repository;

import com.e24online.mdm.domain.TenantUsageSnapshot;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface TenantUsageSnapshotRepository extends CrudRepository<TenantUsageSnapshot, Long> {

    @Query("""
            SELECT *
            FROM tenant_usage_snapshot
            WHERE tenant_master_id = :tenantMasterId
              AND usage_month = :usageMonth
            LIMIT 1
            """)
    Optional<TenantUsageSnapshot> findByTenantAndUsageMonth(
            @Param("tenantMasterId") Long tenantMasterId,
            @Param("usageMonth") LocalDate usageMonth
    );
}

