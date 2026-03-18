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
        noArgs.setDeliveryStatus("ACKED");
        OffsetDateTime at = OffsetDateTime.now();
        noArgs.setAcknowledgedAt(at);

        assertEquals(99L, noArgs.getResponseId());
        assertEquals("ACKED", noArgs.getDeliveryStatus());
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
                new RemediationSummary(1L, 2L, "R1", "Title", "Desc", "TYPE", "ENFORCE", "{\"x\":1}", "OPEN")
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
        noArgs.setRemediationStatus("OPEN");

        assertEquals(1L, noArgs.getEvaluationRemediationId());
        assertEquals(2L, noArgs.getRemediationRuleId());
        assertEquals("CODE", noArgs.getRemediationCode());
        assertEquals("Title", noArgs.getTitle());
        assertEquals("Description", noArgs.getDescription());
        assertEquals("TYPE", noArgs.getRemediationType());
        assertEquals("ENFORCE", noArgs.getEnforceMode());
        assertEquals("{\"a\":1}", noArgs.getInstructionJson());
        assertEquals("OPEN", noArgs.getRemediationStatus());

        RemediationSummary allArgs = new RemediationSummary(
                3L, 4L, "CODE2", "Title2", "Desc2", "TYPE2", "AUDIT", "{\"b\":2}", "DONE"
        );
        assertEquals(3L, allArgs.getEvaluationRemediationId());
        assertEquals(4L, allArgs.getRemediationRuleId());
        assertEquals("CODE2", allArgs.getRemediationCode());
        assertEquals("Title2", allArgs.getTitle());
        assertEquals("Desc2", allArgs.getDescription());
        assertEquals("TYPE2", allArgs.getRemediationType());
        assertEquals("AUDIT", allArgs.getEnforceMode());
        assertEquals("{\"b\":2}", allArgs.getInstructionJson());
        assertEquals("DONE", allArgs.getRemediationStatus());
    }
}
