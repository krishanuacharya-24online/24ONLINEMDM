package com.e24online.mdm.records;

import java.util.List;

/**
 * Response DTO for device history timeline.
 */
public record DeviceTimelineResponse(
    /**
     * Device external ID
     */
    String deviceExternalId,

    /**
     * Device type
     */
    String deviceType,

    /**
     * OS type
     */
    String osType,

    /**
     * Current trust score
     */
    Integer currentScore,

    /**
     * Current score band
     */
    String scoreBand,

    /**
     * Current decision action
     */
    String currentDecision,

    /**
     * Timeline events (sorted by timestamp, newest first)
     */
    List<DeviceTimelineEvent> events,

    /**
     * Total number of events (for pagination)
     */
    Long totalEvents,

    /**
     * Time range start
     */
    java.time.OffsetDateTime startTime,

    /**
     * Time range end
     */
    java.time.OffsetDateTime endTime
) {
}
