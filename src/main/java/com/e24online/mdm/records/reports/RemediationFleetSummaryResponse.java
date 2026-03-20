package com.e24online.mdm.records.reports;

import java.time.OffsetDateTime;

public record RemediationFleetSummaryResponse(
        String scopeTenantId,
        long totalTrackedIssues,
        long openIssues,
        long resolvedIssues,
        long devicesWithOpenIssues,
        long awaitingVerificationIssues,
        long stillOpenIssues,
        long resolvedOnRescanIssues,
        OffsetDateTime latestResolvedAt
) {
}
