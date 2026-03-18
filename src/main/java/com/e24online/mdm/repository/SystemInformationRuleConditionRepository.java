package com.e24online.mdm.repository;

import com.e24online.mdm.domain.SystemInformationRuleCondition;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SystemInformationRuleConditionRepository extends CrudRepository<SystemInformationRuleCondition, Long> {

    @Query("""
            SELECT * FROM system_information_rule_condition
            WHERE system_information_rule_id = :ruleId
              AND is_deleted = false
            ORDER BY condition_group, id
            """)
    List<SystemInformationRuleCondition> findByRuleId(@Param("ruleId") Long ruleId);

    @Query("""
            SELECT * FROM system_information_rule_condition
            WHERE system_information_rule_id IN (:ruleIds)
              AND is_deleted = false
              AND status = 'ACTIVE'
            ORDER BY system_information_rule_id, condition_group, id
            """)
    List<SystemInformationRuleCondition> findActiveByRuleIds(@Param("ruleIds") List<Long> ruleIds);
}
