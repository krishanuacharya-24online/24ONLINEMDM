package com.e24online.mdm.service;

import com.e24online.mdm.domain.DevicePosturePayload;
import com.e24online.mdm.repository.DevicePosturePayloadRepository;
import com.e24online.mdm.web.dto.PosturePayloadIngestRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.scheduler.Schedulers;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostureIngestionServiceTest {

    @Mock
    private DevicePosturePayloadRepository repository;

    @Mock
    private AuditEventService auditEventService;
    @Mock
    private TenantEntitlementService tenantEntitlementService;

    private PostureIngestionService service;

    @BeforeEach
    void setUp() {
        service = new PostureIngestionService(
                repository,
                auditEventService,
                new ObjectMapper(),
                Schedulers.immediate(),
                tenantEntitlementService
        );
    }

    @Test
    void ingest_duplicatePayload_returnsExistingRecordByIdempotencyKey() {
        AtomicLong idSequence = new AtomicLong(100);
        Map<String, DevicePosturePayload> byIdempotencyKey = new HashMap<>();

        when(repository.findByIdempotencyKey(eq("tenant-a"), eq("dev-01"), anyString()))
                .thenAnswer(invocation -> Optional.ofNullable(byIdempotencyKey.get(invocation.getArgument(2))));
        when(repository.save(any(DevicePosturePayload.class))).thenAnswer(invocation -> {
            DevicePosturePayload payload = invocation.getArgument(0);
            payload.setId(idSequence.getAndIncrement());
            byIdempotencyKey.put(payload.getIdempotencyKey(), payload);
            return payload;
        });

        PosturePayloadIngestRequest request = new PosturePayloadIngestRequest();
        request.setDeviceExternalId("dev-01");
        request.setAgentId("agent-01");
        request.setPayloadVersion("1.0");
        request.setPayloadHash("abc123");
        request.setPayloadJson(new ObjectMapper().createObjectNode().put("os_type", "ANDROID"));

        Long firstId = service.ingest("tenant-a", request);
        Long secondId = service.ingest("tenant-a", request);

        assertNotNull(firstId);
        assertNotNull(secondId);
        assertEquals(100L, firstId);
        assertEquals(100L, secondId);

        ArgumentCaptor<DevicePosturePayload> captor = ArgumentCaptor.forClass(DevicePosturePayload.class);
        verify(repository, times(1)).save(captor.capture());

        DevicePosturePayload persisted = captor.getValue();
        assertEquals("RECEIVED", persisted.getProcessStatus());
        assertNotNull(persisted.getCreatedAt());
        assertNotNull(persisted.getIdempotencyKey());
        assertEquals(64, persisted.getIdempotencyKey().length());
        assertEquals("SUPPORTED_WITH_WARNINGS", persisted.getSchemaCompatibilityStatus());
        verify(tenantEntitlementService, times(1)).assertCanIngestPayload("tenant-a");
        verify(tenantEntitlementService, times(1)).recordPayloadAccepted(eq("tenant-a"), any());
    }

    @Test
    void ingest_missingPayloadHash_generatesStableHash() {
        AtomicLong idSequence = new AtomicLong(200);
        when(repository.findByIdempotencyKey(eq("tenant-a"), eq("dev-01"), anyString()))
                .thenReturn(Optional.empty());
        when(repository.save(any(DevicePosturePayload.class))).thenAnswer(invocation -> {
            DevicePosturePayload payload = invocation.getArgument(0);
            payload.setId(idSequence.getAndIncrement());
            return payload;
        });

        PosturePayloadIngestRequest request = new PosturePayloadIngestRequest();
        request.setDeviceExternalId("dev-01");
        request.setAgentId("agent-01");
        request.setPayloadVersion("1.0");
        request.setPayloadHash(null);
        request.setPayloadJson(new ObjectMapper().createObjectNode().put("os_type", "ANDROID"));

        Long id = service.ingest("tenant-a", request);

        assertEquals(200L, id);
        ArgumentCaptor<DevicePosturePayload> captor = ArgumentCaptor.forClass(DevicePosturePayload.class);
        verify(repository).save(captor.capture());
        DevicePosturePayload persisted = captor.getValue();
        assertNotNull(persisted.getPayloadHash());
        assertEquals(64, persisted.getPayloadHash().length());
        assertNotNull(persisted.getIdempotencyKey());
        assertEquals(64, persisted.getIdempotencyKey().length());
        assertEquals("SUPPORTED_WITH_WARNINGS", persisted.getSchemaCompatibilityStatus());
    }

    @Test
    void ingest_rejectsNullPayloadJson() {
        PosturePayloadIngestRequest request = new PosturePayloadIngestRequest();
        request.setDeviceExternalId("dev-01");
        request.setAgentId("agent-01");
        request.setPayloadVersion("1.0");
        request.setPayloadJson(null);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> service.ingest("tenant-a", request)
        );

        assertEquals("payload_json is required", ex.getMessage());
        verify(repository, never()).save(any(DevicePosturePayload.class));
    }

    @Test
    void ingest_rejectsNonObjectPayloadJson() {
        PosturePayloadIngestRequest request = new PosturePayloadIngestRequest();
        request.setDeviceExternalId("dev-01");
        request.setAgentId("agent-01");
        request.setPayloadVersion("1.0");
        request.setPayloadJson(new ObjectMapper().createArrayNode());

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> service.ingest("tenant-a", request)
        );

        assertEquals("payload_json must be a JSON object", ex.getMessage());
        verify(repository, never()).save(any(DevicePosturePayload.class));
    }

    @Test
    void ingest_sanitizesCorruptedInstalledAppNameInsidePayloadJson() {
        when(repository.findByIdempotencyKey(eq("tenant-a"), eq("dev-01"), anyString()))
                .thenReturn(Optional.empty());
        when(repository.save(any(DevicePosturePayload.class))).thenAnswer(invocation -> {
            DevicePosturePayload payload = invocation.getArgument(0);
            payload.setId(300L);
            return payload;
        });

        ObjectMapper mapper = new ObjectMapper();
        PosturePayloadIngestRequest request = new PosturePayloadIngestRequest();
        request.setDeviceExternalId("dev-01");
        request.setAgentId("agent-01");
        request.setPayloadVersion("1.0");
        request.setPayloadJson(mapper.createObjectNode()
                .put("os_type", "WINDOWS")
                .set("installed_apps", mapper.createArrayNode()
                        .add(mapper.createObjectNode()
                                .put("app_name", "\uFFFDTorrent")
                                .put("package_id", "uTorrent"))));

        service.ingest("tenant-a", request);

        ArgumentCaptor<DevicePosturePayload> captor = ArgumentCaptor.forClass(DevicePosturePayload.class);
        verify(repository).save(captor.capture());
        assertEquals("{\"os_type\":\"WINDOWS\",\"installed_apps\":[{\"app_name\":\"uTorrent\",\"package_id\":\"uTorrent\"}]}", captor.getValue().getPayloadJson());
    }
}
