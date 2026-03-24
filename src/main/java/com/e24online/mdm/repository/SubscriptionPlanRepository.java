package com.e24online.mdm.repository;

import com.e24online.mdm.domain.SubscriptionPlan;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SubscriptionPlanRepository extends CrudRepository<SubscriptionPlan, Long> {

    @Query("""
            SELECT *
            FROM subscription_plan
            WHERE id = :id
              AND is_deleted = false
            LIMIT 1
            """)
    Optional<SubscriptionPlan> findAvailableById(@Param("id") Long id);

    @Query("""
            SELECT *
            FROM subscription_plan
            WHERE plan_code = :planCode
              AND is_deleted = false
            LIMIT 1
            """)
    Optional<SubscriptionPlan> findAvailableByPlanCode(@Param("planCode") String planCode);

    @Query("""
            SELECT *
            FROM subscription_plan
            WHERE plan_code = :planCode
              AND is_deleted = false
              AND status = 'ACTIVE'
            LIMIT 1
            """)
    Optional<SubscriptionPlan> findActiveByPlanCode(@Param("planCode") String planCode);

    @Query("""
            SELECT *
            FROM subscription_plan
            WHERE id = :id
              AND is_deleted = false
              AND status = 'ACTIVE'
            LIMIT 1
            """)
    Optional<SubscriptionPlan> findActiveById(@Param("id") Long id);

    @Query("""
            SELECT *
            FROM subscription_plan
            WHERE is_deleted = false
            ORDER BY
              CASE WHEN status = 'ACTIVE' THEN 0 ELSE 1 END,
              plan_code
            """)
    List<SubscriptionPlan> findAllAvailable();

    @Query("""
            SELECT *
            FROM subscription_plan
            WHERE is_deleted = false
              AND status = 'ACTIVE'
            ORDER BY plan_code
            """)
    List<SubscriptionPlan> findAllActive();
}
