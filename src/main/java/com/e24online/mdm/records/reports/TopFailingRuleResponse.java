package com.e24online.mdm.records.reports;

import java.time.OffsetDateTime;

public record TopFailingRuleResponse(
        Long ruleId,
        String ruleCode,
        String ruleTag,
        String ruleDescription,
        String complianceAction,
        long impactedDevices,
        long blockedDevices,
        long currentMatchCount,
        OffsetDateTime latestEvaluatedAt
) {
}
