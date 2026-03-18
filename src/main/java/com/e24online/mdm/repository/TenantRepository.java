package com.e24online.mdm.repository;

import com.e24online.mdm.domain.Tenant;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TenantRepository extends CrudRepository<Tenant, Long> {

    @Query("""
            SELECT * FROM tenant_master
            WHERE is_deleted = false
            ORDER BY tenant_id
            LIMIT :limit OFFSET :offset
            """)
    List<Tenant> findPaged(@Param("limit") int limit, @Param("offset") long offset);

    @Query("""
            SELECT COUNT(*) FROM tenant_master
            WHERE is_deleted = false
            """)
    long countActive();

    Optional<Tenant> findByTenantId(String tenantId);

    @Query("""
            SELECT * FROM tenant_master
            WHERE is_deleted = false
              AND tenant_id = :tenantId
            LIMIT 1
            """)
    Optional<Tenant> findActiveByTenantId(@Param("tenantId") String tenantId);
}
