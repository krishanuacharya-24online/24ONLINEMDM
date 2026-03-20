package com.e24online.mdm.repository;

import com.e24online.mdm.domain.TenantFeatureOverride;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TenantFeatureOverrideRepository extends CrudRepository<TenantFeatureOverride, Long> {

    @Query("""
            SELECT *
            FROM tenant_feature_override
            WHERE tenant_master_id = :tenantMasterId
            ORDER BY feature_key
            """)
    List<TenantFeatureOverride> findByTenantMasterId(@Param("tenantMasterId") Long tenantMasterId);

    @Query("""
            SELECT *
            FROM tenant_feature_override
            WHERE tenant_master_id = :tenantMasterId
              AND feature_key = :featureKey
            LIMIT 1
            """)
    Optional<TenantFeatureOverride> findByTenantMasterIdAndFeatureKey(
            @Param("tenantMasterId") Long tenantMasterId,
            @Param("featureKey") String featureKey
    );
}

