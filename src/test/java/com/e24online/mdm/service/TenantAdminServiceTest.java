package com.e24online.mdm.service;

import com.e24online.mdm.domain.Tenant;
import com.e24online.mdm.domain.TenantApiKey;
import com.e24online.mdm.records.tenant.TenantKeyMetadataResponse;
import com.e24online.mdm.records.tenant.TenantKeyRotateResponse;
import com.e24online.mdm.records.tenant.TenantResponse;
import com.e24online.mdm.repository.TenantApiKeyRepository;
import com.e24online.mdm.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.scheduler.Schedulers;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantAdminServiceTest {

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private TenantApiKeyRepository tenantApiKeyRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private TransactionTemplate transactionTemplate;

    @Mock
    private AuditEventService auditEventService;
    @Mock
    private TenantSubscriptionService tenantSubscriptionService;

    private TenantAdminService service;

    @BeforeEach
    void setUp() {
        service = new TenantAdminService(
                tenantRepository,
                tenantApiKeyRepository,
                passwordEncoder,
                new BlockingDb(Schedulers.immediate()),
                transactionTemplate,
                auditEventService,
                tenantSubscriptionService
        );

        lenient().when(tenantRepository.save(any(Tenant.class))).thenAnswer(invocation -> {
            Tenant tenant = invocation.getArgument(0);
            if (tenant.getId() == null) {
                tenant.setId(100L);
            }
            return tenant;
        });
        lenient().when(tenantApiKeyRepository.save(any(TenantApiKey.class))).thenAnswer(invocation -> {
            TenantApiKey key = invocation.getArgument(0);
            if (key.getId() == null) {
                key.setId(500L);
            }
            if (key.getCreatedAt() == null) {
                key.setCreatedAt(OffsetDateTime.now());
            }
            return key;
        });
        lenient().doAnswer(invocation -> {
            TransactionCallback<Object> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        }).when(transactionTemplate).execute(any(TransactionCallback.class));
        lenient().doAnswer(invocation -> {
            java.util.function.Consumer<TransactionStatus> consumer = invocation.getArgument(0);
            consumer.accept(mock(TransactionStatus.class));
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
    }

    @Test
    void createTenant_createsNewTenantWithNormalizedValues() {
        when(tenantRepository.findByTenantId("acme_1")).thenReturn(Optional.empty());

        TenantResponse response = service.createTenant(
                "  root-admin  ",
                "  ACME_1  ",
                "  Acme   Corporation  ",
                "active"
        ).block();

        assertNotNull(response);
        assertEquals("acme_1", response.tenantId());
        assertEquals("Acme Corporation", response.name());
        assertEquals("ACTIVE", response.status());
        verify(tenantSubscriptionService, times(1)).ensureSubscriptionForTenant(any(Tenant.class), org.mockito.ArgumentMatchers.eq("root-admin"));
    }

    @Test
    void createTenant_throwsConflictWhenTenantAlreadyActive() {
        Tenant existing = activeTenant(12L, "acme_1");
        existing.setDeleted(false);
        when(tenantRepository.findByTenantId("acme_1")).thenReturn(Optional.of(existing));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                service.createTenant("root", "acme_1", "Acme", "ACTIVE").block()
        );

        assertEquals(409, ex.getStatusCode().value());
    }

    @Test
    void createTenant_revivesSoftDeletedTenant() {
        Tenant deletedTenant = activeTenant(12L, "acme_1");
        deletedTenant.setDeleted(true);
        deletedTenant.setStatus("INACTIVE");
        when(tenantRepository.findByTenantId("acme_1")).thenReturn(Optional.of(deletedTenant));

        TenantResponse response = service.createTenant(
                "admin",
                "acme_1",
                "Acme Revived",
                "ACTIVE"
        ).block();

        assertNotNull(response);
        assertEquals(12L, response.id());
        assertFalse(deletedTenant.isDeleted());
        assertEquals("ACTIVE", deletedTenant.getStatus());
    }

    @Test
    void getActiveTenantKey_returnsInactiveMetadataWhenNoKeyExists() {
        when(tenantRepository.findById(5L)).thenReturn(Optional.of(activeTenant(5L, "tenant-a")));
        when(tenantApiKeyRepository.findActiveByTenantMasterId(5L)).thenReturn(Optional.empty());

        TenantKeyMetadataResponse response = service.getActiveTenantKey(5L).block();

        assertNotNull(response);
        assertFalse(response.active());
        assertNull(response.keyHint());
        assertNull(response.createdAt());
    }

    @Test
    void getActiveTenantKey_returnsKeyMetadataWhenActiveKeyExists() {
        when(tenantRepository.findById(5L)).thenReturn(Optional.of(activeTenant(5L, "tenant-a")));
        TenantApiKey key = new TenantApiKey();
        key.setTenantMasterId(5L);
        key.setStatus("ACTIVE");
        key.setKeyHint("tk_abc...xyz");
        key.setCreatedAt(OffsetDateTime.now().minusMinutes(5));
        when(tenantApiKeyRepository.findActiveByTenantMasterId(5L)).thenReturn(Optional.of(key));

        TenantKeyMetadataResponse response = service.getActiveTenantKey(5L).block();

        assertNotNull(response);
        assertTrue(response.active());
        assertEquals("tk_abc...xyz", response.keyHint());
        assertNotNull(response.createdAt());
    }

    @Test
    void deleteTenant_marksTenantInactiveAndRevokesActiveKey() {
        Tenant tenant = activeTenant(8L, "tenant-z");
        when(tenantRepository.findById(8L)).thenReturn(Optional.of(tenant));

        TenantApiKey activeKey = new TenantApiKey();
        activeKey.setTenantMasterId(8L);
        activeKey.setStatus("ACTIVE");
        when(tenantApiKeyRepository.findActiveByTenantMasterId(8L)).thenReturn(Optional.of(activeKey));

        service.deleteTenant(8L, "ops-user").block();

        assertTrue(tenant.isDeleted());
        assertEquals("INACTIVE", tenant.getStatus());
        assertEquals("REVOKED", activeKey.getStatus());
        assertEquals("ops-user", activeKey.getRevokedBy());
    }

    @Test
    void rotateTenantKey_revokesExistingAndPersistsNewActiveKey() {
        Tenant tenant = activeTenant(9L, "tenant-nine");
        when(tenantRepository.findById(9L)).thenReturn(Optional.of(tenant));

        TenantApiKey existing = new TenantApiKey();
        existing.setId(300L);
        existing.setTenantMasterId(9L);
        existing.setStatus("ACTIVE");
        when(tenantApiKeyRepository.findActiveByTenantMasterId(9L)).thenReturn(Optional.of(existing));
        when(passwordEncoder.encode(any())).thenReturn("encoded-key");

        TenantKeyRotateResponse response = service.rotateTenantKey(9L, "admin-user").block();

        assertNotNull(response);
        assertEquals(9L, response.tenantMasterId());
        assertTrue(response.key().startsWith("tk_tenant-nine_"));
        assertNotNull(response.keyHint());

        assertEquals("REVOKED", existing.getStatus());
        assertEquals("admin-user", existing.getRevokedBy());

        ArgumentCaptor<TenantApiKey> keyCaptor = ArgumentCaptor.forClass(TenantApiKey.class);
        verify(tenantApiKeyRepository, times(2)).save(keyCaptor.capture());
        List<TenantApiKey> saved = keyCaptor.getAllValues();
        TenantApiKey newKey = saved.get(1);
        assertEquals("ACTIVE", newKey.getStatus());
        assertEquals("encoded-key", newKey.getKeyHash());
        assertEquals("admin-user", newKey.getCreatedBy());
    }

    @Test
    void listTenants_appliesSafePagingBounds() {
        Tenant row = activeTenant(77L, "tenant-77");
        row.setName("Tenant 77");
        when(tenantRepository.findPaged(500, 0L)).thenReturn(List.of(row));

        List<TenantResponse> response = service.listTenants(-10, 9999).collectList().block();

        assertNotNull(response);
        assertEquals(1, response.size());
        assertEquals("tenant-77", response.getFirst().tenantId());
        verify(tenantRepository).findPaged(500, 0L);
    }

    private Tenant activeTenant(Long id, String tenantCode) {
        Tenant tenant = new Tenant();
        tenant.setId(id);
        tenant.setTenantId(tenantCode);
        tenant.setName("Tenant Name");
        tenant.setStatus("ACTIVE");
        tenant.setDeleted(false);
        return tenant;
    }
}
