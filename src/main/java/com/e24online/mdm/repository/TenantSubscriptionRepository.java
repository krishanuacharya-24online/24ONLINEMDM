package com.e24online.mdm.repository;

import com.e24online.mdm.domain.TenantSubscription;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface TenantSubscriptionRepository extends CrudRepository<TenantSubscription, Long> {

    @Query("""
            SELECT *
            FROM tenant_subscription
            WHERE tenant_master_id = :tenantMasterId
            LIMIT 1
            """)
    Optional<TenantSubscription> findByTenantMasterId(@Param("tenantMasterId") Long tenantMasterId);
}

