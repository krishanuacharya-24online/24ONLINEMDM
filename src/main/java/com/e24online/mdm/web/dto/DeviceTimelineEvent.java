package com.e24online.mdm.web.dto;

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
    public enum EventType {
        SCORE_CHANGE,
        EVALUATION,
        DECISION,
        REMEDIATION,
        LIFECYCLE,
        ENROLLMENT,
        POSTURE_SUBMISSION
    }

    public enum EventCategory {
        SCORE,        // Trust score changes
        SECURITY,     // Security events (root, emulator, etc.)
        APPLICATION,  // App-related events
        LIFECYCLE,    // OS lifecycle events
        DECISION,     // Decision changes
        REMEDIATION,  // Remediation events
        SYSTEM        // System events
    }

    public enum Severity {
        INFO,       // Normal events
        WARNING,    // Caution events
        CRITICAL    // Critical events
    }
}
