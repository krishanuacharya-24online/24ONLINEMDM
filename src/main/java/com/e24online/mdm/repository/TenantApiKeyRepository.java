package com.e24online.mdm.repository;

import com.e24online.mdm.domain.TenantApiKey;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TenantApiKeyRepository extends CrudRepository<TenantApiKey, Long> {

    @Query("""
            SELECT * FROM tenant_api_key
            WHERE tenant_master_id = :tenantMasterId
              AND status = 'ACTIVE'
            ORDER BY created_at DESC, id DESC
            LIMIT 1
            """)
    Optional<TenantApiKey> findActiveByTenantMasterId(@Param("tenantMasterId") Long tenantMasterId);

    @Query("""
            SELECT * FROM tenant_api_key
            WHERE tenant_master_id = :tenantMasterId
            ORDER BY created_at DESC, id DESC
            LIMIT :limit
            """)
    List<TenantApiKey> findRecentByTenantMasterId(
            @Param("tenantMasterId") Long tenantMasterId,
            @Param("limit") int limit
    );
}
