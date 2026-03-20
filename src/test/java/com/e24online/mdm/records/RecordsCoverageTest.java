package com.e24online.mdm.records;

import com.e24online.mdm.enums.Role;
import com.e24online.mdm.records.lookup.LookupRow;
import com.e24online.mdm.records.lookup.LookupUpdateRequest;
import com.e24online.mdm.records.lookup.LookupUpsertRequest;
import com.e24online.mdm.records.operations.QueueHealthEntryResponse;
import com.e24online.mdm.records.operations.QueueHealthSummaryResponse;
import com.e24online.mdm.records.operations.PipelineDailyTrendResponse;
import com.e24online.mdm.records.operations.PipelineFailureCategoryResponse;
import com.e24online.mdm.records.operations.PipelineOperabilitySummaryResponse;
import com.e24online.mdm.records.posture.evaluation.AppliedPolicy;
import com.e24online.mdm.records.posture.evaluation.EvaluationComputation;
import com.e24online.mdm.records.posture.evaluation.LifecycleResolution;
import com.e24online.mdm.records.posture.evaluation.MatchDraft;
import com.e24online.mdm.records.posture.evaluation.ParsedPosture;
import com.e24online.mdm.records.posture.evaluation.RemediationCandidate;
import com.e24online.mdm.records.posture.evaluation.RemediationStatusTransition;
import com.e24online.mdm.records.posture.evaluation.SavedMatch;
import com.e24online.mdm.records.posture.evaluation.SavedRemediation;
import com.e24online.mdm.records.posture.evaluation.ScoreSignal;
import com.e24online.mdm.records.reports.AgentCapabilityCoverageResponse;
import com.e24online.mdm.records.reports.AgentVersionDistributionResponse;
import com.e24online.mdm.records.reports.FleetOperationalSummaryResponse;
import com.e24online.mdm.records.reports.RemediationFleetSummaryResponse;
import com.e24online.mdm.records.reports.ScoreTrendPointResponse;
import com.e24online.mdm.records.reports.TopFailingRuleResponse;
import com.e24online.mdm.records.reports.TopRiskyApplicationResponse;
import com.e24online.mdm.records.tenant.TenantContext;
import com.e24online.mdm.records.tenant.SubscriptionPlanResponse;
import com.e24online.mdm.records.tenant.TenantFeatureOverrideRequest;
import com.e24online.mdm.records.tenant.TenantFeatureOverrideResponse;
import com.e24online.mdm.records.tenant.TenantKeyMetadataResponse;
import com.e24online.mdm.records.tenant.TenantKeyRotateResponse;
import com.e24online.mdm.records.tenant.TenantResponse;
import com.e24online.mdm.records.tenant.TenantSubscriptionResponse;
import com.e24online.mdm.records.tenant.TenantSubscriptionUpsertRequest;
import com.e24online.mdm.records.tenant.TenantUpsertRequest;
import com.e24online.mdm.records.tenant.TenantUsageResponse;
import com.e24online.mdm.records.ui.DataTablePage;
import com.e24online.mdm.records.ui.PageParams;
import com.e24online.mdm.records.user.AccessScope;
import com.e24online.mdm.records.user.UserPrincipal;
import com.e24online.mdm.records.user.UserResponse;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecordsCoverageTest {

    @Test
    void pageParams_of_usesDefaultSizeWhenSizeIsNonPositive() {
        PageParams params = PageParams.of(-5, 0, 25, 200, 10);

        assertEquals(25, params.limit());
        assertEquals(0L, params.offset());
    }

    @Test
    void pageParams_of_capsPageAndSize() {
        PageParams params = PageParams.of(999, 500, 25, 100, 10);

        assertEquals(100, params.limit());
        assertEquals(1000L, params.offset());
    }

    @Test
    void coreRecords_canBeConstructed() {
        AccessScope scope = new AccessScope("admin", "PRODUCT_ADMIN", 1L, true, false);
        assertEquals("admin", scope.actor());
        assertTrue(scope.productAdmin());
        assertFalse(scope.tenantAdmin());

        CreateSetupKeyRequest createSetup = new CreateSetupKeyRequest(5, 7L, 60);
        assertEquals(5, createSetup.maxUses());
        assertEquals(7L, createSetup.targetUserId());
        assertEquals(60, createSetup.ttlMinutes());

        DataTablePage page = new DataTablePage(1, 10L, 8L, List.of(Map.of("k", "v")));
        assertEquals(1, page.draw());
        assertEquals(10L, page.recordsTotal());
        assertEquals(8L, page.recordsFiltered());
        assertEquals("v", page.data().getFirst().get("k"));

        DeEnrollRequest deEnroll = new DeEnrollRequest("retired");
        assertEquals("retired", deEnroll.reason());

        QrClaimRequest qrClaim = new QrClaimRequest("qr-token", "agent-1", "fp", "label", "tenant-a");
        assertEquals("qr-token", qrClaim.qrToken());
        assertEquals("agent-1", qrClaim.agentId());
        assertEquals("fp", qrClaim.deviceFingerprint());
        assertEquals("label", qrClaim.deviceLabel());
        assertEquals("tenant-a", qrClaim.tenantId());

        SetupKeyClaimRequest setupKeyClaim = new SetupKeyClaimRequest("setup-key", "agent-1", "fp", "label", "tenant-a");
        assertEquals("setup-key", setupKeyClaim.setupKey());
        assertEquals("agent-1", setupKeyClaim.agentId());
        assertEquals("fp", setupKeyClaim.deviceFingerprint());
        assertEquals("label", setupKeyClaim.deviceLabel());
        assertEquals("tenant-a", setupKeyClaim.tenantId());

        Role[] roles = Role.values();
        assertEquals(4, roles.length);
        assertEquals(Role.AUDITOR, Role.valueOf("AUDITOR"));
        assertEquals(Role.PRODUCT_ADMIN, Role.valueOf("PRODUCT_ADMIN"));
    }

    @Test
    void lookupRecords_canBeConstructed() {
        LookupRow row = new LookupRow("os_type", "win", "Windows");
        assertEquals("os_type", row.lookupType());
        assertEquals("win", row.code());
        assertEquals("Windows", row.description());

        LookupUpdateRequest updateRequest = new LookupUpdateRequest("Updated");
        assertEquals("Updated", updateRequest.description());

        LookupUpsertRequest upsertRequest = new LookupUpsertRequest("mac", "macOS");
        assertEquals("mac", upsertRequest.code());
        assertEquals("macOS", upsertRequest.description());
    }

    @Test
    void tenantAndUserRecords_canBeConstructed() {
        TenantContext context = new TenantContext("tenant-a", 9L);
        assertEquals("tenant-a", context.tenantId());
        assertEquals(9L, context.ownerUserId());

        OffsetDateTime now = OffsetDateTime.now();
        TenantKeyMetadataResponse metadata = new TenantKeyMetadataResponse(11L, "tenant-a", true, "hint", now);
        assertEquals(11L, metadata.tenantMasterId());
        assertEquals("tenant-a", metadata.tenantId());
        assertTrue(metadata.active());
        assertEquals("hint", metadata.keyHint());
        assertEquals(now, metadata.createdAt());

        TenantKeyRotateResponse rotate = new TenantKeyRotateResponse(11L, "tenant-a", "key", "hint", now);
        assertEquals(11L, rotate.tenantMasterId());
        assertEquals("tenant-a", rotate.tenantId());
        assertEquals("key", rotate.key());
        assertEquals("hint", rotate.keyHint());
        assertEquals(now, rotate.createdAt());

        TenantResponse tenantResponse = new TenantResponse(1L, "tenant-a", "Tenant A", "ACTIVE");
        assertEquals(1L, tenantResponse.id());
        assertEquals("tenant-a", tenantResponse.tenantId());
        assertEquals("Tenant A", tenantResponse.name());
        assertEquals("ACTIVE", tenantResponse.status());

        TenantUpsertRequest tenantUpsert = new TenantUpsertRequest("tenant-a", "Tenant A", "ACTIVE");
        assertEquals("tenant-a", tenantUpsert.tenantId());
        assertEquals("Tenant A", tenantUpsert.name());
        assertEquals("ACTIVE", tenantUpsert.status());

        SubscriptionPlanResponse planResponse = new SubscriptionPlanResponse(
                "TRIAL", "Trial", "desc", 25, 10, 5000L, 30, false, false
        );
        assertEquals("TRIAL", planResponse.planCode());
        assertEquals("Trial", planResponse.planName());
        assertEquals("desc", planResponse.description());

        TenantFeatureOverrideRequest overrideRequest = new TenantFeatureOverrideRequest(true, now.plusDays(1), "manual");
        assertTrue(overrideRequest.enabled());
        assertEquals("manual", overrideRequest.reason());

        TenantFeatureOverrideResponse overrideResponse = new TenantFeatureOverrideResponse(
                "PREMIUM_REPORTING", true, now.plusDays(1), "manual"
        );
        assertEquals("PREMIUM_REPORTING", overrideResponse.featureKey());
        assertTrue(overrideResponse.enabled());

        TenantSubscriptionUpsertRequest subscriptionUpsert = new TenantSubscriptionUpsertRequest(
                "TRIAL", "TRIALING", now, now.plusDays(30), now.plusDays(37), "notes"
        );
        assertEquals("TRIAL", subscriptionUpsert.planCode());
        assertEquals("TRIALING", subscriptionUpsert.subscriptionState());

        TenantSubscriptionResponse subscriptionResponse = new TenantSubscriptionResponse(
                1L, "tenant-a", "TRIAL", "Trial", "TRIALING", 25, 10, 5000L,
                30, false, false, now, now.plusDays(30), now.plusDays(37), "notes", List.of(overrideResponse)
        );
        assertEquals("TRIAL", subscriptionResponse.planCode());
        assertEquals("TRIALING", subscriptionResponse.subscriptionState());
        assertEquals("notes", subscriptionResponse.notes());
        assertEquals(1, subscriptionResponse.featureOverrides().size());

        TenantUsageResponse tenantUsageResponse = new TenantUsageResponse(
                1L, "tenant-a", now.toLocalDate(), 3L, 4L, 5L, 25, 10, 5000L, false, false, false
        );
        assertEquals("tenant-a", tenantUsageResponse.tenantId());
        assertEquals(5L, tenantUsageResponse.posturePayloadCount());

        UserPrincipal principal = new UserPrincipal(1L, "user", "TENANT_ADMIN", 99L);
        assertEquals(1L, principal.id());
        assertEquals("user", principal.username());
        assertEquals("TENANT_ADMIN", principal.role());
        assertEquals(99L, principal.tenantId());

        UserResponse user = new UserResponse(1L, "user", "TENANT_ADMIN", "ACTIVE", "tenant-a");
        assertEquals(1L, user.id());
        assertEquals("user", user.username());
        assertEquals("TENANT_ADMIN", user.role());
        assertEquals("ACTIVE", user.status());
        assertEquals("tenant-a", user.tenantId());

        RemediationFleetSummaryResponse fleetSummary = new RemediationFleetSummaryResponse(
                "tenant-a", 10L, 6L, 4L, 3L, 2L, 1L, 4L, now
        );
        assertEquals("tenant-a", fleetSummary.scopeTenantId());
        assertEquals(10L, fleetSummary.totalTrackedIssues());
        assertEquals(6L, fleetSummary.openIssues());
        assertEquals(4L, fleetSummary.resolvedIssues());
        assertEquals(3L, fleetSummary.devicesWithOpenIssues());
        assertEquals(2L, fleetSummary.awaitingVerificationIssues());
        assertEquals(1L, fleetSummary.stillOpenIssues());
        assertEquals(4L, fleetSummary.resolvedOnRescanIssues());
        assertEquals(now, fleetSummary.latestResolvedAt());

        FleetOperationalSummaryResponse operationalSummary = new FleetOperationalSummaryResponse(
                "tenant-a", 72, 25L, 7L, 4L, 1L, 3L, 18L, 2L, 1L, 4L
        );
        assertEquals("tenant-a", operationalSummary.scopeTenantId());
        assertEquals(72, operationalSummary.staleAfterHours());
        assertEquals(25L, operationalSummary.totalDevices());
        assertEquals(7L, operationalSummary.staleDevices());
        assertEquals(4L, operationalSummary.highRiskDevices());
        assertEquals(1L, operationalSummary.criticalDevices());
        assertEquals(3L, operationalSummary.lifecycleRiskDevices());
        assertEquals(18L, operationalSummary.supportedDevices());
        assertEquals(2L, operationalSummary.eolDevices());
        assertEquals(1L, operationalSummary.eeolDevices());
        assertEquals(4L, operationalSummary.notTrackedDevices());

        TopFailingRuleResponse topFailingRule = new TopFailingRuleResponse(
                44L, "RULE-44", "OS", "Outdated OS", "BLOCK", 4L, 2L, 5L, now
        );
        assertEquals(44L, topFailingRule.ruleId());
        assertEquals("RULE-44", topFailingRule.ruleCode());
        assertEquals("OS", topFailingRule.ruleTag());
        assertEquals("Outdated OS", topFailingRule.ruleDescription());
        assertEquals("BLOCK", topFailingRule.complianceAction());
        assertEquals(4L, topFailingRule.impactedDevices());
        assertEquals(2L, topFailingRule.blockedDevices());
        assertEquals(5L, topFailingRule.currentMatchCount());
        assertEquals(now, topFailingRule.latestEvaluatedAt());

        TopRiskyApplicationResponse topRiskyApplication = new TopRiskyApplicationResponse(
                "AnyDesk", "com.anydesk", "AnyDesk", "WINDOWS", "REMOTE_ACCESS", 3L, 1L, 3L, now
        );
        assertEquals("AnyDesk", topRiskyApplication.appName());
        assertEquals("com.anydesk", topRiskyApplication.packageId());
        assertEquals("AnyDesk", topRiskyApplication.publisher());
        assertEquals("WINDOWS", topRiskyApplication.appOsType());
        assertEquals("REMOTE_ACCESS", topRiskyApplication.policyTag());
        assertEquals(3L, topRiskyApplication.impactedDevices());
        assertEquals(1L, topRiskyApplication.blockedDevices());
        assertEquals(3L, topRiskyApplication.currentMatchCount());
        assertEquals(now, topRiskyApplication.latestEvaluatedAt());

        AgentVersionDistributionResponse agentVersion = new AgentVersionDistributionResponse(
                "6.2.1", "SUPPORTED_WITH_WARNINGS", 9L, 7L, now
        );
        assertEquals("6.2.1", agentVersion.agentVersion());
        assertEquals("SUPPORTED_WITH_WARNINGS", agentVersion.schemaCompatibilityStatus());
        assertEquals(9L, agentVersion.deviceCount());
        assertEquals(7L, agentVersion.devicesWithCapabilities());
        assertEquals(now, agentVersion.latestCaptureTime());

        AgentCapabilityCoverageResponse capabilityCoverage = new AgentCapabilityCoverageResponse(
                "payload_ack", 6L, now
        );
        assertEquals("payload_ack", capabilityCoverage.capabilityKey());
        assertEquals(6L, capabilityCoverage.deviceCount());
        assertEquals(now, capabilityCoverage.latestCaptureTime());

        ScoreTrendPointResponse scoreTrendPoint = new ScoreTrendPointResponse(
                LocalDate.now().minusDays(1), 11L, 8L, 73.5, 6L, 2L, 1L, 2L
        );
        assertEquals(11L, scoreTrendPoint.evaluationCount());
        assertEquals(8L, scoreTrendPoint.distinctDevices());
        assertEquals(73.5, scoreTrendPoint.averageTrustScore());
        assertEquals(6L, scoreTrendPoint.allowCount());
        assertEquals(2L, scoreTrendPoint.notifyCount());
        assertEquals(1L, scoreTrendPoint.quarantineCount());
        assertEquals(2L, scoreTrendPoint.blockCount());

        QueueHealthEntryResponse queueHealthEntry = new QueueHealthEntryResponse(
                "POSTURE_EVALUATION", "posture.queue", "posture.dlq", 5L, 1L, 2L, 2, 6, "DLQ_BACKLOG", null
        );
        assertEquals("POSTURE_EVALUATION", queueHealthEntry.pipelineKey());
        assertEquals("posture.queue", queueHealthEntry.queueName());
        assertEquals("posture.dlq", queueHealthEntry.deadLetterQueueName());
        assertEquals(5L, queueHealthEntry.readyMessages());
        assertEquals(1L, queueHealthEntry.deadLetterMessages());
        assertEquals(2L, queueHealthEntry.activeConsumers());
        assertEquals(2, queueHealthEntry.configuredConsumers());
        assertEquals(6, queueHealthEntry.maxConsumers());
        assertEquals("DLQ_BACKLOG", queueHealthEntry.status());

        QueueHealthSummaryResponse queueHealthSummary = new QueueHealthSummaryResponse(
                now, "DEGRADED", 5L, 1L, List.of(queueHealthEntry)
        );
        assertEquals(now, queueHealthSummary.checkedAt());
        assertEquals("DEGRADED", queueHealthSummary.overallStatus());
        assertEquals(5L, queueHealthSummary.totalReadyMessages());
        assertEquals(1L, queueHealthSummary.totalDeadLetterMessages());
        assertEquals(1, queueHealthSummary.queues().size());

        PipelineOperabilitySummaryResponse pipelineSummary = new PipelineOperabilitySummaryResponse(
                now, 4L, 1L, 2L, 1L, 7L, 3L, 2L, 5L, now.minusHours(2), 120L
        );
        assertEquals(now, pipelineSummary.checkedAt());
        assertEquals(4L, pipelineSummary.inFlightPayloads());
        assertEquals(1L, pipelineSummary.receivedPayloads());
        assertEquals(2L, pipelineSummary.queuedPayloads());
        assertEquals(1L, pipelineSummary.validatedPayloads());
        assertEquals(7L, pipelineSummary.failedPayloads());
        assertEquals(3L, pipelineSummary.failedLast24Hours());
        assertEquals(2L, pipelineSummary.queueFailuresLast7Days());
        assertEquals(5L, pipelineSummary.evaluationFailuresLast7Days());
        assertEquals(120L, pipelineSummary.oldestInFlightAgeMinutes());

        PipelineFailureCategoryResponse failureCategory = new PipelineFailureCategoryResponse(
                "QUEUE_PUBLISH", 3L, now, "Queue publish failed: broker unavailable"
        );
        assertEquals("QUEUE_PUBLISH", failureCategory.categoryKey());
        assertEquals(3L, failureCategory.failureCount());
        assertEquals(now, failureCategory.latestFailureAt());
        assertEquals("Queue publish failed: broker unavailable", failureCategory.sampleProcessError());

        PipelineDailyTrendResponse pipelineTrend = new PipelineDailyTrendResponse(
                LocalDate.now().minusDays(1), 12L, 10L, 2L, 9L, 1L, 3L
        );
        assertEquals(12L, pipelineTrend.ingestSuccessCount());
        assertEquals(10L, pipelineTrend.queueSuccessCount());
        assertEquals(2L, pipelineTrend.queueFailureCount());
        assertEquals(9L, pipelineTrend.evaluationSuccessCount());
        assertEquals(1L, pipelineTrend.evaluationFailureCount());
        assertEquals(3L, pipelineTrend.failedPayloadCount());
    }

    @Test
    void postureEvaluationRecords_canBeConstructed() {
        AppliedPolicy appliedPolicy = new AppliedPolicy(null);
        assertNull(appliedPolicy.policy());

        MatchDraft matchDraft = new MatchDraft(
                "SYSTEM_RULE", 1L, 2L, 3L, 4L, "SUPPORTED", 5L, (short) 7, "ALLOW", (short) -10, "details"
        );
        assertEquals("SYSTEM_RULE", matchDraft.matchSource());
        assertEquals(1L, matchDraft.systemRuleId());
        assertEquals(2L, matchDraft.rejectApplicationId());
        assertEquals(3L, matchDraft.trustScorePolicyId());
        assertEquals(4L, matchDraft.osReleaseLifecycleMasterId());
        assertEquals("SUPPORTED", matchDraft.osLifecycleState());
        assertEquals(5L, matchDraft.deviceInstalledApplicationId());
        assertEquals((short) 7, matchDraft.severity());
        assertEquals("ALLOW", matchDraft.complianceAction());
        assertEquals((short) -10, matchDraft.scoreDelta());
        assertEquals("details", matchDraft.matchDetail());

        ScoreSignal scoreSignal = new ScoreSignal(
                "RULE", 10L, 3L, 1L, 2L, 4L, "SUPPORTED", (short) -5, "note"
        );
        assertEquals("RULE", scoreSignal.eventSource());
        assertEquals(10L, scoreSignal.sourceRecordId());
        assertEquals(3L, scoreSignal.trustScorePolicyId());
        assertEquals(1L, scoreSignal.systemRuleId());
        assertEquals(2L, scoreSignal.rejectApplicationId());
        assertEquals(4L, scoreSignal.osReleaseLifecycleMasterId());
        assertEquals("SUPPORTED", scoreSignal.osLifecycleState());
        assertEquals((short) -5, scoreSignal.scoreDelta());
        assertEquals("note", scoreSignal.notes());

        EvaluationComputation computation = new EvaluationComputation(
                (short) 80, (short) 70, (short) -10, 3, 1, "ALLOW", "ok", false, 9L,
                List.of(matchDraft), List.of(scoreSignal)
        );
        assertEquals((short) 80, computation.scoreBefore());
        assertEquals((short) 70, computation.scoreAfter());
        assertEquals((short) -10, computation.scoreDeltaTotal());
        assertEquals(3, computation.matchedRuleCount());
        assertEquals(1, computation.matchedAppCount());
        assertEquals("ALLOW", computation.decisionAction());
        assertEquals("ok", computation.decisionReason());
        assertFalse(computation.remediationRequired());
        assertEquals(9L, computation.decisionPolicyId());
        assertEquals(1, computation.matches().size());
        assertEquals(1, computation.scoreSignals().size());

        LifecycleResolution lifecycle = new LifecycleResolution(4L, "SUPPORTED", "LIFECYCLE");
        assertEquals(4L, lifecycle.masterId());
        assertEquals("SUPPORTED", lifecycle.state());
        assertEquals("LIFECYCLE", lifecycle.signalKey());

        OffsetDateTime captureTime = OffsetDateTime.now();
        ParsedPosture parsedPosture = new ParsedPosture(
                "tenant-a", "dev-1", "agent-1", "ANDROID", "Android", "14", "Q1", "PHONE", "UTC",
                "6.x", 34, "build-1", "Google", false, false, false, captureTime, null, null
        );
        assertEquals("tenant-a", parsedPosture.tenantId());
        assertEquals("dev-1", parsedPosture.deviceExternalId());
        assertEquals("agent-1", parsedPosture.agentId());
        assertEquals("ANDROID", parsedPosture.osType());
        assertEquals("Android", parsedPosture.osName());
        assertEquals("14", parsedPosture.osVersion());
        assertEquals("Q1", parsedPosture.osCycle());
        assertEquals("PHONE", parsedPosture.deviceType());
        assertEquals("UTC", parsedPosture.timeZone());
        assertEquals("6.x", parsedPosture.kernelVersion());
        assertEquals(34, parsedPosture.apiLevel());
        assertEquals("build-1", parsedPosture.osBuildNumber());
        assertEquals("Google", parsedPosture.manufacturer());
        assertFalse(parsedPosture.rootDetected());
        assertFalse(parsedPosture.runningOnEmulator());
        assertFalse(parsedPosture.usbDebuggingStatus());
        assertEquals(captureTime, parsedPosture.captureTime());
        assertNull(parsedPosture.root());
        assertNull(parsedPosture.installedApps());

        RemediationCandidate remediationCandidate = new RemediationCandidate(null, 100L, "RULE");
        assertNull(remediationCandidate.mapping());
        assertEquals(100L, remediationCandidate.matchId());
        assertEquals("RULE", remediationCandidate.sourceType());

        SavedMatch savedMatch = new SavedMatch(null, matchDraft);
        assertNull(savedMatch.match());
        assertEquals(matchDraft, savedMatch.draft());

        SavedRemediation savedRemediation = new SavedRemediation(null, null, "ENFORCE");
        assertNull(savedRemediation.remediation());
        assertNull(savedRemediation.rule());
        assertEquals("ENFORCE", savedRemediation.enforceMode());

        RemediationStatusTransition transition = new RemediationStatusTransition(
                12L, 13L, 14L, "MATCH", "SYSTEM_RULE", "DELIVERED", "STILL_OPEN", captureTime
        );
        assertEquals(12L, transition.remediationId());
        assertEquals(13L, transition.postureEvaluationRunId());
        assertEquals(14L, transition.remediationRuleId());
        assertEquals("MATCH", transition.sourceType());
        assertEquals("SYSTEM_RULE", transition.matchSource());
        assertEquals("DELIVERED", transition.fromStatus());
        assertEquals("STILL_OPEN", transition.toStatus());
        assertEquals(captureTime, transition.completedAt());
    }
}
