package com.e24online.mdm.web.dto;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebDtoCoverageTest {

    @Test
    void createEvaluationRunRequest_roundTripProperties() {
        CreateEvaluationRunRequest request = new CreateEvaluationRunRequest();
        request.setPayloadId(42L);
        request.setForceRecalculate(true);
        request.setRequestedBy("admin");

        assertEquals(42L, request.getPayloadId());
        assertTrue(request.isForceRecalculate());
        assertEquals("admin", request.getRequestedBy());
    }

    @Test
    void decisionAckRequest_roundTripProperties() {
        DecisionAckRequest request = new DecisionAckRequest();
        OffsetDateTime now = OffsetDateTime.now();
        request.setDeliveryStatus("DELIVERED");
        request.setAcknowledgedAt(now);
        request.setErrorMessage("none");

        assertEquals("DELIVERED", request.getDeliveryStatus());
        assertEquals(now, request.getAcknowledgedAt());
        assertEquals("none", request.getErrorMessage());
    }

    @Test
    void decisionAckResponse_constructorsAndProperties() {
        DecisionAckResponse noArgs = new DecisionAckResponse();
        noArgs.setResponseId(99L);
        noArgs.setDeliveryStatus("ACKNOWLEDGED");
        OffsetDateTime at = OffsetDateTime.now();
        noArgs.setAcknowledgedAt(at);

        assertEquals(99L, noArgs.getResponseId());
        assertEquals("ACKNOWLEDGED", noArgs.getDeliveryStatus());
        assertEquals(at, noArgs.getAcknowledgedAt());

        DecisionAckResponse allArgs = new DecisionAckResponse(100L, "FAILED", at);
        assertEquals(100L, allArgs.getResponseId());
        assertEquals("FAILED", allArgs.getDeliveryStatus());
        assertEquals(at, allArgs.getAcknowledgedAt());
    }

    @Test
    void lookupDtos_constructorsAndProperties() {
        LookupEntryDto entryNoArgs = new LookupEntryDto();
        entryNoArgs.setLookupType("os_type");
        entryNoArgs.setCode("win");
        entryNoArgs.setDescription("Windows");

        assertEquals("os_type", entryNoArgs.getLookupType());
        assertEquals("win", entryNoArgs.getCode());
        assertEquals("Windows", entryNoArgs.getDescription());

        LookupEntryDto entryAllArgs = new LookupEntryDto("app_status", "blk", "Blocked");
        assertEquals("app_status", entryAllArgs.getLookupType());
        assertEquals("blk", entryAllArgs.getCode());
        assertEquals("Blocked", entryAllArgs.getDescription());

        LookupTypeInfo typeNoArgs = new LookupTypeInfo();
        typeNoArgs.setLookupType("os_type");
        typeNoArgs.setDescription("Operating system type");
        assertEquals("os_type", typeNoArgs.getLookupType());
        assertEquals("Operating system type", typeNoArgs.getDescription());

        LookupTypeInfo typeAllArgs = new LookupTypeInfo("app_status", "Application status");
        assertEquals("app_status", typeAllArgs.getLookupType());
        assertEquals("Application status", typeAllArgs.getDescription());
    }

    @Test
    void posturePayloadIngestRequest_toStringAndProperties() throws Exception {
        PosturePayloadIngestRequest request = new PosturePayloadIngestRequest();
        request.setDeviceExternalId("device-1");
        request.setAgentId("agent-1");
        request.setPayloadVersion("v1");
        request.setPayloadHash("hash-1");
        request.setPayloadJson(new ObjectMapper().readTree("{\"k\":\"v\"}"));

        assertEquals("device-1", request.getDeviceExternalId());
        assertEquals("agent-1", request.getAgentId());
        assertEquals("v1", request.getPayloadVersion());
        assertEquals("hash-1", request.getPayloadHash());
        assertNotNull(request.getPayloadJson());

        String text = request.toString();
        assertTrue(text.contains("deviceExternalId='device-1'"));
        assertTrue(text.contains("agentId='agent-1'"));
        assertTrue(text.contains("payloadVersion='v1'"));
        assertTrue(text.contains("payloadHash='hash-1'"));
        assertTrue(text.contains("payloadJson="));
    }

    @Test
    void posturePayloadIngestResponse_remediationSetterHandlesNullAndNonNull() {
        PosturePayloadIngestResponse noArgs = new PosturePayloadIngestResponse();
        assertNotNull(noArgs.getRemediation());
        assertTrue(noArgs.getRemediation().isEmpty());
        noArgs.setResultStatusUrl("/v1/agent/posture-payloads/10/result");
        assertEquals("/v1/agent/posture-payloads/10/result", noArgs.getResultStatusUrl());

        List<RemediationSummary> remediationList = List.of(
                new RemediationSummary(1L, 2L, "R1", "Title", "Desc", "TYPE", "ENFORCE", "{\"x\":1}", "PROPOSED")
        );
        noArgs.setRemediation(remediationList);
        assertSame(remediationList, noArgs.getRemediation());

        noArgs.setRemediation(null);
        assertNotNull(noArgs.getRemediation());
        assertTrue(noArgs.getRemediation().isEmpty());

        PosturePayloadIngestResponse allArgs = new PosturePayloadIngestResponse(
                10L, "INGESTED", 20L, 30L, "ALLOW", (short) 90, "ok", false, remediationList
        );
        assertEquals(10L, allArgs.getPayloadId());
        assertEquals("INGESTED", allArgs.getStatus());
        assertEquals(20L, allArgs.getEvaluationRunId());
        assertEquals(30L, allArgs.getDecisionResponseId());
        assertEquals("ALLOW", allArgs.getDecisionAction());
        assertEquals((short) 90, allArgs.getTrustScore());
        assertEquals("ok", allArgs.getDecisionReason());
        assertFalse(allArgs.isRemediationRequired());
        assertSame(remediationList, allArgs.getRemediation());
        assertNull(allArgs.getResultStatusUrl());
    }

    @Test
    void remediationSummary_constructorsAndProperties() {
        RemediationSummary noArgs = new RemediationSummary();
        noArgs.setEvaluationRemediationId(1L);
        noArgs.setRemediationRuleId(2L);
        noArgs.setRemediationCode("CODE");
        noArgs.setTitle("Title");
        noArgs.setDescription("Description");
        noArgs.setRemediationType("TYPE");
        noArgs.setEnforceMode("ENFORCE");
        noArgs.setInstructionJson("{\"a\":1}");
        noArgs.setRemediationStatus("PROPOSED");

        assertEquals(1L, noArgs.getEvaluationRemediationId());
        assertEquals(2L, noArgs.getRemediationRuleId());
        assertEquals("CODE", noArgs.getRemediationCode());
        assertEquals("Title", noArgs.getTitle());
        assertEquals("Description", noArgs.getDescription());
        assertEquals("TYPE", noArgs.getRemediationType());
        assertEquals("ENFORCE", noArgs.getEnforceMode());
        assertEquals("{\"a\":1}", noArgs.getInstructionJson());
        assertEquals("PROPOSED", noArgs.getRemediationStatus());

        RemediationSummary allArgs = new RemediationSummary(
                3L, 4L, "CODE2", "Title2", "Desc2", "TYPE2", "AUDIT", "{\"b\":2}", "CLOSED"
        );
        assertEquals(3L, allArgs.getEvaluationRemediationId());
        assertEquals(4L, allArgs.getRemediationRuleId());
        assertEquals("CODE2", allArgs.getRemediationCode());
        assertEquals("Title2", allArgs.getTitle());
        assertEquals("Desc2", allArgs.getDescription());
        assertEquals("TYPE2", allArgs.getRemediationType());
        assertEquals("AUDIT", allArgs.getEnforceMode());
        assertEquals("{\"b\":2}", allArgs.getInstructionJson());
        assertEquals("CLOSED", allArgs.getRemediationStatus());
    }

    @Test
    void simplePolicyDtos_roundTripProperties() {
        SimpleDevicePolicyRequest deviceRequest = new SimpleDevicePolicyRequest();
        deviceRequest.setName("Rooted Android device");
        deviceRequest.setSeverity((short) 5);
        deviceRequest.setFieldName("root_detected");
        deviceRequest.setOperator("EQ");
        deviceRequest.setValueType("BOOLEAN");
        deviceRequest.setValueBoolean(true);
        deviceRequest.setRemediationRuleId(10L);
        assertEquals("Rooted Android device", deviceRequest.getName());
        assertEquals((short) 5, deviceRequest.getSeverity());
        assertTrue(deviceRequest.getValueBoolean());
        assertEquals(10L, deviceRequest.getRemediationRuleId());

        SimpleDevicePolicySummary deviceSummary = new SimpleDevicePolicySummary();
        deviceSummary.setId(1L);
        deviceSummary.setRuleCode("SR-1");
        deviceSummary.setComplex(true);
        deviceSummary.setComplexityReason("Multiple conditions");
        assertEquals(1L, deviceSummary.getId());
        assertEquals("SR-1", deviceSummary.getRuleCode());
        assertTrue(deviceSummary.isComplex());
        assertEquals("Multiple conditions", deviceSummary.getComplexityReason());

        SimpleAppPolicyRequest appRequest = new SimpleAppPolicyRequest();
        appRequest.setPolicyTag("Block remote admin tool");
        appRequest.setAppName("AnyDesk");
        appRequest.setSeverity((short) 4);
        appRequest.setRemediationRuleId(11L);
        assertEquals("Block remote admin tool", appRequest.getPolicyTag());
        assertEquals("AnyDesk", appRequest.getAppName());
        assertEquals((short) 4, appRequest.getSeverity());
        assertEquals(11L, appRequest.getRemediationRuleId());

        SimpleAppPolicySummary appSummary = new SimpleAppPolicySummary();
        appSummary.setId(2L);
        appSummary.setPolicyTag("Block remote admin tool");
        appSummary.setComplex(false);
        assertEquals(2L, appSummary.getId());
        assertEquals("Block remote admin tool", appSummary.getPolicyTag());
        assertFalse(appSummary.isComplex());

        SimplePolicyStarterPackSummary starterPackSummary = new SimplePolicyStarterPackSummary();
        starterPackSummary.setScope("tenant-a");
        starterPackSummary.setCreatedDeviceChecks(3);
        starterPackSummary.setCreatedTrustLevels(4);
        starterPackSummary.setCreatedFixes(3);
        assertEquals("tenant-a", starterPackSummary.getScope());
        assertEquals(3, starterPackSummary.getCreatedDeviceChecks());
        assertEquals(4, starterPackSummary.getCreatedTrustLevels());
        assertEquals(3, starterPackSummary.getCreatedFixes());

        SimplePolicySimulationAppInput appInput = new SimplePolicySimulationAppInput();
        appInput.setAppName("AnyDesk");
        appInput.setPackageId("AnyDeskSoftwareGmbH.AnyDesk");
        appInput.setAppVersion("1.0.0");
        appInput.setPublisher("AnyDesk");
        appInput.setAppOsType("WINDOWS");
        assertEquals("AnyDesk", appInput.getAppName());
        assertEquals("AnyDeskSoftwareGmbH.AnyDesk", appInput.getPackageId());

        SimplePolicySimulationRequest simulationRequest = new SimplePolicySimulationRequest();
        simulationRequest.setCurrentScore(80);
        simulationRequest.setOsType("WINDOWS");
        simulationRequest.setOsCycle("23H2");
        simulationRequest.setInstalledApps(List.of(appInput));
        assertEquals(80, simulationRequest.getCurrentScore());
        assertEquals("WINDOWS", simulationRequest.getOsType());
        assertEquals("23H2", simulationRequest.getOsCycle());
        assertEquals(1, simulationRequest.getInstalledApps().size());
        simulationRequest.setInstalledApps(null);
        assertNotNull(simulationRequest.getInstalledApps());
        assertTrue(simulationRequest.getInstalledApps().isEmpty());

        SimplePolicySimulationFinding finding = new SimplePolicySimulationFinding();
        finding.setCategory("DEVICE_CHECK");
        finding.setTitle("Rooted Android device");
        finding.setDetail("Matched device posture rule.");
        finding.setSeverity((short) 5);
        finding.setAction("BLOCK");
        finding.setScoreDelta((short) -60);
        assertEquals("DEVICE_CHECK", finding.getCategory());
        assertEquals("Rooted Android device", finding.getTitle());
        assertEquals((short) -60, finding.getScoreDelta());

        SimplePolicySimulationResponse simulationResponse = new SimplePolicySimulationResponse();
        simulationResponse.setDecisionAction("BLOCK");
        simulationResponse.setLifecycleState("EEOL");
        simulationResponse.setFindings(List.of(finding));
        assertEquals("BLOCK", simulationResponse.getDecisionAction());
        assertEquals("EEOL", simulationResponse.getLifecycleState());
        assertEquals(1, simulationResponse.getFindings().size());
        simulationResponse.setFindings(null);
        assertNotNull(simulationResponse.getFindings());
        assertTrue(simulationResponse.getFindings().isEmpty());
    }
}
