package com.e24online.mdm.domain;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;

@Setter
@Getter
@Table("device_trust_score_event")
public class DeviceTrustScoreEvent {

    @Id
    private Long id;

    @Column("device_trust_profile_id")
    private Long deviceTrustProfileId;

    @Column("event_source")
    private String eventSource;

    @Column("source_record_id")
    private Long sourceRecordId;

    @Column("trust_score_policy_id")
    private Long trustScorePolicyId;

    @Column("system_information_rule_id")
    private Long systemInformationRuleId;

    @Column("reject_application_list_id")
    private Long rejectApplicationListId;

    @Column("os_release_lifecycle_master_id")
    private Long osReleaseLifecycleMasterId;

    @Column("os_lifecycle_state")
    private String osLifecycleState;

    @Column("score_before")
    private Short scoreBefore;

    @Column("score_delta")
    private Short scoreDelta;

    @Column("score_after")
    private Short scoreAfter;

    @Column("event_time")
    private OffsetDateTime eventTime;

    @Column("processed_at")
    private OffsetDateTime processedAt;

    @Column("processed_by")
    private String processedBy;

    @Column("notes")
    private String notes;

}

