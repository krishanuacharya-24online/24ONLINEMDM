package com.e24online.mdm.records.devices;

import com.e24online.mdm.enums.EventCategory;
import com.e24online.mdm.enums.EventType;
import com.e24online.mdm.enums.Severity;

import java.time.OffsetDateTime;

/**
 * DTO for device history timeline visualization.
 * Combines trust score events, evaluation runs, and decision changes
 * into a unified timeline view.
 */
public record DeviceTimelineEvent(
    /**
     * Unique event ID
     */
    Long id,

    /**
     * Event timestamp
     */
    OffsetDateTime timestamp,

    /**
     * Event type: SCORE_CHANGE, EVALUATION, DECISION, REMEDIATION, LIFECYCLE
     */
    EventType type,

    /**
     * Trust score before the event
     */
    Integer scoreBefore,

    /**
     * Trust score after the event
     */
    Integer scoreAfter,

    /**
     * Score change delta
     */
    Integer scoreDelta,

    /**
     * Device decision action (ALLOW, NOTIFY, QUARANTINE, BLOCK)
     */
    String decisionAction,

    /**
     * Event title for display
     */
    String title,

    /**
     * Detailed description
     */
    String description,

    /**
     * Event category for icon/color coding
     */
    EventCategory category,

    /**
     * Severity level: INFO, WARNING, CRITICAL
     */
    Severity severity,

    /**
     * Associated rule or policy name
     */
    String ruleName,

    /**
     * Remediation required flag
     */
    Boolean remediationRequired,

    /**
     * Additional metadata as JSON string
     */
    String metadata
) {

}
