package com.e24online.mdm.web;

import com.e24online.mdm.service.DeviceTimelineService;
import com.e24online.mdm.web.dto.DeviceTimelineResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for device history timeline.
 */
@RestController
@RequestMapping("/v1/devices")
public class DeviceTimelineController {

    private final DeviceTimelineService timelineService;

    public DeviceTimelineController(DeviceTimelineService timelineService) {
        this.timelineService = timelineService;
    }

    /**
     * Get device history timeline.
     *
     * @param profileId Device trust profile ID
     * @param limit     Maximum number of events to return (default: 50, max: 200)
     * @return Timeline with aggregated events
     */
    @GetMapping("/{profileId}/timeline")
    public ResponseEntity<DeviceTimelineResponse> getDeviceTimeline(
            @PathVariable Long profileId,
            @RequestParam(defaultValue = "50") int limit,
            Authentication authentication
    ) {
        // Apply limits
        int safeLimit = Math.clamp(limit, 1, 200);

        DeviceTimelineResponse response = timelineService.getTimeline(profileId, safeLimit);

        return ResponseEntity.ok(response);
    }
}
