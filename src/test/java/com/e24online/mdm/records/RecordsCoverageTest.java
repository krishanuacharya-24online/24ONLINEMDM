package com.e24online.mdm.records;

import com.e24online.mdm.enums.Role;
import com.e24online.mdm.records.lookup.LookupRow;
import com.e24online.mdm.records.lookup.LookupUpdateRequest;
import com.e24online.mdm.records.lookup.LookupUpsertRequest;
import com.e24online.mdm.records.posture.evaluation.AppliedPolicy;
import com.e24online.mdm.records.posture.evaluation.EvaluationComputation;
import com.e24online.mdm.records.posture.evaluation.LifecycleResolution;
import com.e24online.mdm.records.posture.evaluation.MatchDraft;
import com.e24online.mdm.records.posture.evaluation.ParsedPosture;
import com.e24online.mdm.records.posture.evaluation.RemediationCandidate;
import com.e24online.mdm.records.posture.evaluation.SavedMatch;
import com.e24online.mdm.records.posture.evaluation.SavedRemediation;
import com.e24online.mdm.records.posture.evaluation.ScoreSignal;
import com.e24online.mdm.records.tenant.TenantContext;
import com.e24online.mdm.records.tenant.TenantKeyMetadataResponse;
import com.e24online.mdm.records.tenant.TenantKeyRotateResponse;
import com.e24online.mdm.records.tenant.TenantResponse;
import com.e24online.mdm.records.tenant.TenantUpsertRequest;
import com.e24online.mdm.records.ui.DataTablePage;
import com.e24online.mdm.records.ui.PageParams;
import com.e24online.mdm.records.user.AccessScope;
import com.e24online.mdm.records.user.UserPrincipal;
import com.e24online.mdm.records.user.UserResponse;
import org.junit.jupiter.api.Test;

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
    }
}
