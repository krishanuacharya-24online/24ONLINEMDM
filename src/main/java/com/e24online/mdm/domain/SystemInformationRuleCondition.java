package com.e24online.mdm.domain;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;

@Setter
@Getter
@Table("system_information_rule_condition")
public class SystemInformationRuleCondition {

    @Id
    private Long id;

    @Column("system_information_rule_id")
    private Long systemInformationRuleId;

    @Column("condition_group")
    private Short conditionGroup;

    @Column("field_name")
    private String fieldName;

    @Column("operator")
    private String operator;

    @Column("value_text")
    private String valueText;

    @Column("value_numeric")
    private Double valueNumeric;

    @Column("value_boolean")
    private Boolean valueBoolean;

    @Column("value_json")
    private String valueJson;

    @Column("weight")
    private Short weight;

    @Column("status")
    private String status;

    @Column("is_deleted")
    private boolean deleted;

    @Column("created_at")
    private OffsetDateTime createdAt;

    @Column("created_by")
    private String createdBy;

    @Column("modified_at")
    private OffsetDateTime modifiedAt;

    @Column("modified_by")
    private String modifiedBy;

}
