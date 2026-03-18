package com.e24online.mdm.web;

import com.e24online.mdm.domain.DeviceDecisionResponse;
import com.e24online.mdm.domain.DevicePosturePayload;
import com.e24online.mdm.domain.Tenant;
import com.e24online.mdm.domain.TenantApiKey;
import com.e24online.mdm.repository.DeviceDecisionResponseRepository;
import com.e24online.mdm.repository.DevicePosturePayloadRepository;
import com.e24online.mdm.repository.TenantApiKeyRepository;
import com.e24online.mdm.repository.TenantRepository;
import com.e24online.mdm.service.BlockingDb;
import com.e24online.mdm.service.DeviceEnrollmentService;
import com.e24online.mdm.service.WorkflowOrchestrationService;
import com.e24online.mdm.web.dto.DecisionAckRequest;
import com.e24online.mdm.web.dto.PosturePayloadIngestRequest;
import com.e24online.mdm.web.dto.PosturePayloadIngestResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import tools.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentControllerTest {

    @Mock
    private WorkflowOrchestrationService workflowService;

    @Mock
    private DevicePosturePayloadRepository payloadRepository;

    @Mock
    private DeviceDecisionResponseRepository decisionRepository;

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private TenantApiKeyRepository tenantApiKeyRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private DeviceEnrollmentService enrollmentService;

    private AgentController controller;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @BeforeEach
    void setUp() {
        controller = new AgentController(
                workflowService,
                payloadRepository,
                decisionRepository,
                tenantRepository,
                tenantApiKeyRepository,
                passwordEncoder,
                enrollmentService,
                new BlockingDb(Schedulers.immediate()),
                300L,
                15L
        );
    }

    @Test
    void listPosturePayloads_rejectsInvalidProcessStatus() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                controller.listPosturePayloads("tenant-a", "secret", null, "invalid", 0, 10)
        );

        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    void listPosturePayloads_requiresTenantHeader() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                controller.listPosturePayloads("   ", "secret", null, null, 0, 10)
        );

        assertEquals(401, ex.getStatusCode().value());
    }

    @Test
    void listPosturePayloads_successWithActiveTenantKey() {
        Tenant tenant = activeTenant(1L, "tenant-a");
        TenantApiKey activeKey = activeKey(1L, "active-hash");
        DevicePosturePayload payload = new DevicePosturePayload();
        payload.setId(101L);
        payload.setTenantId("tenant-a");
        payload.setProcessStatus("RECEIVED");

        when(tenantRepository.findActiveByTenantId("tenant-a")).thenReturn(Optional.of(tenant));
        when(tenantApiKeyRepository.findActiveByTenantMasterId(1L)).thenReturn(Optional.of(activeKey));
        when(passwordEncoder.matches("secret", "active-hash")).thenReturn(true);
        when(passwordEncoder.upgradeEncoding("active-hash")).thenReturn(false);
        when(payloadRepository.findPaged("tenant-a", null, null, 50, 0L)).thenReturn(List.of(payload));

        List<DevicePosturePayload> response = controller
                .listPosturePayloads("tenant-a", "secret", null, null, 0, 50)
                .collectList()
                .block();

        assertNotNull(response);
        assertEquals(1, response.size());
        assertEquals(101L, response.getFirst().getId());
    }

    @Test
    void listPosturePayloads_acceptsGraceKeyWhenRecentlyRevoked() {
        Tenant tenant = activeTenant(1L, "tenant-a");
        TenantApiKey activeKey = activeKey(1L, "active-hash");
        TenantApiKey revoked = new TenantApiKey();
        revoked.setTenantMasterId(1L);
        revoked.setStatus("REVOKED");
        revoked.setKeyHash("revoked-hash");
        revoked.setRevokedAt(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1));
        DevicePosturePayload payload = new DevicePosturePayload();
        payload.setId(102L);

        when(tenantRepository.findActiveByTenantId("tenant-a")).thenReturn(Optional.of(tenant));
        when(tenantApiKeyRepository.findActiveByTenantMasterId(1L)).thenReturn(Optional.of(activeKey));
        when(passwordEncoder.matches("old-secret", "active-hash")).thenReturn(false);
        when(tenantApiKeyRepository.findRecentByTenantMasterId(1L, 5)).thenReturn(List.of(revoked));
        when(passwordEncoder.matches("old-secret", "revoked-hash")).thenReturn(true);
        when(payloadRepository.findPaged("tenant-a", null, null, 50, 0L)).thenReturn(List.of(payload));

        List<DevicePosturePayload> response = controller
                .listPosturePayloads("tenant-a", "old-secret", null, null, 0, 50)
                .collectList()
                .block();

        assertNotNull(response);
        assertEquals(1, response.size());
    }

    @Test
    void listPosturePayloads_upgradesTenantKeyHashWhenEncoderRequestsUpgrade() {
        Tenant tenant = activeTenant(1L, "tenant-a");
        TenantApiKey activeKey = activeKey(1L, "legacy-hash");
        when(tenantRepository.findActiveByTenantId("tenant-a")).thenReturn(Optional.of(tenant));
        when(tenantApiKeyRepository.findActiveByTenantMasterId(1L)).thenReturn(Optional.of(activeKey));
        when(passwordEncoder.matches("secret", "legacy-hash")).thenReturn(true);
        when(passwordEncoder.upgradeEncoding("legacy-hash")).thenReturn(true);
        when(passwordEncoder.encode("secret")).thenReturn("new-hash");
        when(payloadRepository.findPaged("tenant-a", null, null, 50, 0L)).thenReturn(List.of());

        List<DevicePosturePayload> response = controller
                .listPosturePayloads("tenant-a", "secret", null, null, 0, 50)
                .collectList()
                .block();

        assertNotNull(response);
        assertEquals(0, response.size());
        assertEquals("new-hash", activeKey.getKeyHash());
        verify(tenantApiKeyRepository, times(1)).save(activeKey);
    }

    @Test
    void ingestPosturePayload_withDeviceTokenRejectsDeviceMismatch() {
        PosturePayloadIngestRequest request = ingestRequest("dev-1");
        when(enrollmentService.authenticateDeviceTokenAsync("device-token"))
                .thenReturn(Mono.just(new DeviceEnrollmentService.DeviceTokenPrincipal("tenant-a", "dev-2", 77L)));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                controller.ingestPosturePayload(
                        null,
                        null,
                        "device-token",
                        MockServerHttpRequest.post("/v1/agent/posture-payloads").build(),
                        Mono.just(request)
                ).block()
        );

        assertEquals(401, ex.getStatusCode().value());
    }

    @Test
    void ingestPosturePayload_tenantHeaderPathReturnsWorkflowResponse() {
        PosturePayloadIngestRequest request = ingestRequest("dev-1");
        PosturePayloadIngestResponse workflowResponse = new PosturePayloadIngestResponse();
        workflowResponse.setPayloadId(333L);
        workflowResponse.setStatus("QUEUED");

        Tenant tenant = activeTenant(1L, "tenant-a");
        TenantApiKey activeKey = activeKey(1L, "hash");
        when(tenantRepository.findActiveByTenantId("tenant-a")).thenReturn(Optional.of(tenant));
        when(tenantApiKeyRepository.findActiveByTenantMasterId(1L)).thenReturn(Optional.of(activeKey));
        when(passwordEncoder.matches("secret", "hash")).thenReturn(true);
        when(passwordEncoder.upgradeEncoding("hash")).thenReturn(false);
        when(enrollmentService.ensureActiveEnrollmentAsync("tenant-a", "dev-1")).thenReturn(Mono.empty());
        when(workflowService.ingestAndQueueAsync("tenant-a", request)).thenReturn(Mono.just(workflowResponse));

        var response = controller
                .ingestPosturePayload(
                        "tenant-a",
                        "secret",
                        null,
                        MockServerHttpRequest.post("/v1/agent/posture-payloads").build(),
                        Mono.just(request)
                )
                .block();

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(333L, response.getBody().getPayloadId());
        assertEquals("/v1/agent/posture-payloads/333/result", response.getBody().getResultStatusUrl());
    }

    @Test
    void acknowledgeDecision_rejectsInvalidDeliveryStatus() {
        Tenant tenant = activeTenant(1L, "tenant-a");
        TenantApiKey activeKey = activeKey(1L, "hash");
        DeviceDecisionResponse existing = new DeviceDecisionResponse();
        existing.setId(99L);
        existing.setSentAt(OffsetDateTime.now(ZoneOffset.UTC));

        when(tenantRepository.findActiveByTenantId("tenant-a")).thenReturn(Optional.of(tenant));
        when(tenantApiKeyRepository.findActiveByTenantMasterId(1L)).thenReturn(Optional.of(activeKey));
        when(passwordEncoder.matches("secret", "hash")).thenReturn(true);
        when(passwordEncoder.upgradeEncoding("hash")).thenReturn(false);
        when(decisionRepository.findByIdAndTenant(99L, "tenant-a")).thenReturn(Optional.of(existing));

        DecisionAckRequest request = new DecisionAckRequest();
        request.setDeliveryStatus("bad-status");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                controller.acknowledgeDecision("tenant-a", "secret", 99L, Mono.just(request)).block()
        );

        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    void acknowledgeDecision_rejectsAckBeforeSentAt() {
        Tenant tenant = activeTenant(1L, "tenant-a");
        TenantApiKey activeKey = activeKey(1L, "hash");
        OffsetDateTime sentAt = OffsetDateTime.now(ZoneOffset.UTC);
        DeviceDecisionResponse existing = new DeviceDecisionResponse();
        existing.setId(44L);
        existing.setSentAt(sentAt);

        when(tenantRepository.findActiveByTenantId("tenant-a")).thenReturn(Optional.of(tenant));
        when(tenantApiKeyRepository.findActiveByTenantMasterId(1L)).thenReturn(Optional.of(activeKey));
        when(passwordEncoder.matches("secret", "hash")).thenReturn(true);
        when(passwordEncoder.upgradeEncoding("hash")).thenReturn(false);
        when(decisionRepository.findByIdAndTenant(44L, "tenant-a")).thenReturn(Optional.of(existing));

        DecisionAckRequest request = new DecisionAckRequest();
        request.setDeliveryStatus("ACKED");
        request.setAcknowledgedAt(sentAt.minusMinutes(1));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                controller.acknowledgeDecision("tenant-a", "secret", 44L, Mono.just(request)).block()
        );

        assertEquals(400, ex.getStatusCode().value());
        verify(decisionRepository, never()).save(any(DeviceDecisionResponse.class));
    }

    @Test
    void getLatestDecision_rejectsBlankDeviceExternalId() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                controller.getLatestDecision("tenant-a", "secret", "   ").block()
        );
        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    void getLatestDecision_returnsLatestRecord() {
        Tenant tenant = activeTenant(1L, "tenant-a");
        TenantApiKey activeKey = activeKey(1L, "hash");
        DeviceDecisionResponse decision = new DeviceDecisionResponse();
        decision.setId(123L);
        decision.setTenantId("tenant-a");
        decision.setDeviceExternalId("dev-1");

        when(tenantRepository.findActiveByTenantId("tenant-a")).thenReturn(Optional.of(tenant));
        when(tenantApiKeyRepository.findActiveByTenantMasterId(1L)).thenReturn(Optional.of(activeKey));
        when(passwordEncoder.matches("secret", "hash")).thenReturn(true);
        when(passwordEncoder.upgradeEncoding("hash")).thenReturn(false);
        when(decisionRepository.findLatestByDevice("tenant-a", "dev-1")).thenReturn(Optional.of(decision));

        DeviceDecisionResponse result = controller
                .getLatestDecision("tenant-a", "secret", "dev-1")
                .block();

        assertNotNull(result);
        assertEquals(123L, result.getId());
    }

    @Test
    void getPosturePayload_missing_returnsNotFound() {
        Tenant tenant = activeTenant(1L, "tenant-a");
        TenantApiKey activeKey = activeKey(1L, "hash");

        when(tenantRepository.findActiveByTenantId("tenant-a")).thenReturn(Optional.of(tenant));
        when(tenantApiKeyRepository.findActiveByTenantMasterId(1L)).thenReturn(Optional.of(activeKey));
        when(passwordEncoder.matches("secret", "hash")).thenReturn(true);
        when(passwordEncoder.upgradeEncoding("hash")).thenReturn(false);
        when(payloadRepository.findByIdAndTenant(999L, "tenant-a")).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                controller.getPosturePayload("tenant-a", "secret", 999L).block()
        );

        assertEquals(404, ex.getStatusCode().value());
    }

    @Test
    void getLatestDecision_missing_returnsNotFound() {
        Tenant tenant = activeTenant(1L, "tenant-a");
        TenantApiKey activeKey = activeKey(1L, "hash");

        when(tenantRepository.findActiveByTenantId("tenant-a")).thenReturn(Optional.of(tenant));
        when(tenantApiKeyRepository.findActiveByTenantMasterId(1L)).thenReturn(Optional.of(activeKey));
        when(passwordEncoder.matches("secret", "hash")).thenReturn(true);
        when(passwordEncoder.upgradeEncoding("hash")).thenReturn(false);
        when(decisionRepository.findLatestByDevice("tenant-a", "dev-404")).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                controller.getLatestDecision("tenant-a", "secret", "dev-404").block()
        );

        assertEquals(404, ex.getStatusCode().value());
    }

    @Test
    void acknowledgeDecision_missingResponse_returnsNotFound() {
        Tenant tenant = activeTenant(1L, "tenant-a");
        TenantApiKey activeKey = activeKey(1L, "hash");

        when(tenantRepository.findActiveByTenantId("tenant-a")).thenReturn(Optional.of(tenant));
        when(tenantApiKeyRepository.findActiveByTenantMasterId(1L)).thenReturn(Optional.of(activeKey));
        when(passwordEncoder.matches("secret", "hash")).thenReturn(true);
        when(passwordEncoder.upgradeEncoding("hash")).thenReturn(false);
        when(decisionRepository.findByIdAndTenant(404L, "tenant-a")).thenReturn(Optional.empty());

        DecisionAckRequest request = new DecisionAckRequest();
        request.setDeliveryStatus("ACKED");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                controller.acknowledgeDecision("tenant-a", "secret", 404L, Mono.just(request)).block()
        );

        assertEquals(404, ex.getStatusCode().value());
    }

    @Test
    void getPosturePayloadResult_returnsWorkflowStatusForPayload() {
        Tenant tenant = activeTenant(1L, "tenant-a");
        TenantApiKey activeKey = activeKey(1L, "hash");
        PosturePayloadIngestResponse response = new PosturePayloadIngestResponse();
        response.setPayloadId(18L);
        response.setStatus("QUEUED");

        when(tenantRepository.findActiveByTenantId("tenant-a")).thenReturn(Optional.of(tenant));
        when(tenantApiKeyRepository.findActiveByTenantMasterId(1L)).thenReturn(Optional.of(activeKey));
        when(passwordEncoder.matches("secret", "hash")).thenReturn(true);
        when(passwordEncoder.upgradeEncoding("hash")).thenReturn(false);
        when(workflowService.getPayloadResultAsync("tenant-a", 18L)).thenReturn(Mono.just(response));

        PosturePayloadIngestResponse result = controller
                .getPosturePayloadResult(
                        "tenant-a",
                        "secret",
                        MockServerHttpRequest.get("/v1/agent/posture-payloads/18/result").build(),
                        18L
                )
                .block();

        assertNotNull(result);
        assertEquals(18L, result.getPayloadId());
        assertEquals("QUEUED", result.getStatus());
        assertEquals("/v1/agent/posture-payloads/18/result", result.getResultStatusUrl());
    }

    private PosturePayloadIngestRequest ingestRequest(String deviceExternalId) {
        PosturePayloadIngestRequest request = new PosturePayloadIngestRequest();
        request.setDeviceExternalId(deviceExternalId);
        request.setAgentId("agent-1");
        request.setPayloadVersion("v1");
        request.setPayloadHash("hash");
        request.setPayloadJson(OBJECT_MAPPER.createObjectNode());
        return request;
    }

    private Tenant activeTenant(Long id, String tenantId) {
        Tenant tenant = new Tenant();
        tenant.setId(id);
        tenant.setTenantId(tenantId);
        tenant.setStatus("ACTIVE");
        tenant.setDeleted(false);
        return tenant;
    }

    private TenantApiKey activeKey(Long tenantMasterId, String hash) {
        TenantApiKey key = new TenantApiKey();
        key.setTenantMasterId(tenantMasterId);
        key.setStatus("ACTIVE");
        key.setKeyHash(hash);
        key.setRevokedAt(null);
        return key;
    }
}
