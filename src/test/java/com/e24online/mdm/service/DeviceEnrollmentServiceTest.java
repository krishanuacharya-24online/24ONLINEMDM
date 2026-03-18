package com.e24online.mdm.service;

import com.e24online.mdm.domain.AuthUser;
import com.e24online.mdm.domain.DeviceAgentCredential;
import com.e24online.mdm.domain.DeviceEnrollment;
import com.e24online.mdm.domain.DeviceSetupKey;
import com.e24online.mdm.domain.Tenant;
import com.e24online.mdm.repository.AuthUserRepository;
import com.e24online.mdm.repository.DeviceAgentCredentialRepository;
import com.e24online.mdm.repository.DeviceEnrollmentRepository;
import com.e24online.mdm.repository.DeviceSetupKeyRepository;
import com.e24online.mdm.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.scheduler.Schedulers;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceEnrollmentServiceTest {

    @Mock
    private DeviceEnrollmentRepository enrollmentRepository;
    @Mock
    private DeviceSetupKeyRepository setupKeyRepository;
    @Mock
    private DeviceAgentCredentialRepository credentialRepository;
    @Mock
    private AuthUserRepository authUserRepository;
    @Mock
    private TenantRepository tenantRepository;
    @Mock
    private NamedParameterJdbcTemplate jdbc;
    @Mock
    private TransactionTemplate transactionTemplate;
    @Mock
    private AuditEventService auditEventService;

    private DeviceEnrollmentService service;

    @BeforeEach
    void setUp() {
        service = new DeviceEnrollmentService(
                enrollmentRepository,
                setupKeyRepository,
                credentialRepository,
                authUserRepository,
                tenantRepository,
                jdbc,
                new BlockingDb(Schedulers.immediate()),
                transactionTemplate,
                auditEventService,
                1440L
        );

        lenient().when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });

        lenient().when(setupKeyRepository.save(any(DeviceSetupKey.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(enrollmentRepository.save(any(DeviceEnrollment.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(credentialRepository.save(any(DeviceAgentCredential.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void createSetupKeyAsync_persistsDefaultsAndReturnsIssuedKey() {
        Tenant tenant = tenant(99L, "tenant-a", "ACTIVE");
        when(tenantRepository.findActiveByTenantId("tenant-a")).thenReturn(Optional.of(tenant));
        when(authUserRepository.findActiveByIdAndTenantId(1L, 99L)).thenReturn(Optional.of(user(1L, 99L, "TENANT_ADMIN")));
        when(authUserRepository.findActiveByIdAndTenantId(2L, 99L)).thenReturn(Optional.of(user(2L, 99L, "TENANT_USER")));
        when(setupKeyRepository.save(any(DeviceSetupKey.class))).thenAnswer(invocation -> {
            DeviceSetupKey key = invocation.getArgument(0);
            key.setId(123L);
            return key;
        });

        DeviceEnrollmentService.SetupKeyIssue issue = service
                .createSetupKeyAsync("Tenant-A", 1L, 2L, null, null, null)
                .block();

        assertNotNull(issue);
        assertEquals(123L, issue.setupKeyId());
        assertEquals(5, issue.maxUses());
        assertEquals(2L, issue.targetUserId());
        assertEquals(1L, issue.issuedByUserId());
        assertTrue(issue.setupKey().matches("^[A-Z0-9]{3}(?:-[A-Z0-9]{3}){3}$"));
        assertNotNull(issue.keyHint());
    }

    @Test
    void createSetupKeyAsync_outOfRangeMaxUsesRejected() {
        Tenant tenant = tenant(99L, "tenant-a", "ACTIVE");
        when(tenantRepository.findActiveByTenantId("tenant-a")).thenReturn(Optional.of(tenant));
        when(authUserRepository.findActiveByIdAndTenantId(1L, 99L)).thenReturn(Optional.of(user(1L, 99L, "TENANT_ADMIN")));
        when(authUserRepository.findActiveByIdAndTenantId(2L, 99L)).thenReturn(Optional.of(user(2L, 99L, "TENANT_USER")));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                service.createSetupKeyAsync("tenant-a", 1L, 2L, "actor", 0, 10).block()
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void listEnrollmentsAsync_normalizesArguments() {
        DeviceEnrollment enrollment = enrollment(10L, "tenant-a", "ENR-1", "ACTIVE", 5L);
        when(enrollmentRepository.findPagedByTenant("tenant-a", "ACTIVE", 5L, 500, 0L))
                .thenReturn(List.of(enrollment));

        List<DeviceEnrollment> rows = service
                .listEnrollmentsAsync("Tenant-A", "active", 5L, -3, 2000)
                .collectList()
                .block();

        assertEquals(1, rows.size());
        verify(enrollmentRepository, times(1))
                .findPagedByTenant("tenant-a", "ACTIVE", 5L, 500, 0L);
    }

    @Test
    void listEnrollmentsAsync_invalidStatusRejected() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                service.listEnrollmentsAsync("tenant-a", "UNKNOWN", null, 0, 50).collectList().block()
        );
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void getEnrollmentAsync_ownerScopeMismatchReturns404() {
        DeviceEnrollment enrollment = enrollment(20L, "tenant-a", "ENR-20", "ACTIVE", 99L);
        when(enrollmentRepository.findByIdAndTenant(20L, "tenant-a")).thenReturn(Optional.of(enrollment));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                service.getEnrollmentAsync("tenant-a", 20L, 1L).block()
        );

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void deEnrollAsync_activeEnrollmentRevokesCredentialsAndSoftDeletesProfile() {
        DeviceEnrollment enrollment = enrollment(21L, "tenant-a", "ENR-21", "ACTIVE", 10L);
        DeviceAgentCredential activeCredential = activeCredential(100L, "tenant-a", 21L, OffsetDateTime.now().plusDays(1));
        when(enrollmentRepository.findByIdAndTenant(21L, "tenant-a")).thenReturn(Optional.of(enrollment));
        when(credentialRepository.findActiveByEnrollmentId(21L)).thenReturn(List.of(activeCredential));
        when(jdbc.update(contains("UPDATE device_trust_profile"), any(org.springframework.jdbc.core.namedparam.MapSqlParameterSource.class)))
                .thenReturn(1);

        DeviceEnrollment result = service
                .deEnrollAsync("tenant-a", "admin-user", 10L, 21L, "retired")
                .block();

        assertEquals("DE_ENROLLED", result.getStatus());
        assertEquals("retired", result.getDeEnrollReason());
        assertEquals("REVOKED", activeCredential.getStatus());
        assertEquals("admin-user", activeCredential.getRevokedBy());
        verify(credentialRepository, times(1)).save(activeCredential);
    }

    @Test
    void deEnrollAsync_alreadyDeEnrolledNoFurtherSideEffects() {
        DeviceEnrollment enrollment = enrollment(22L, "tenant-a", "ENR-22", "DE_ENROLLED", 10L);
        when(enrollmentRepository.findByIdAndTenant(22L, "tenant-a")).thenReturn(Optional.of(enrollment));

        DeviceEnrollment result = service.deEnrollAsync("tenant-a", "actor", 10L, 22L, "reason").block();

        assertEquals("DE_ENROLLED", result.getStatus());
        verify(credentialRepository, never()).findActiveByEnrollmentId(any());
        verify(jdbc, never()).update(anyString(), any(org.springframework.jdbc.core.namedparam.MapSqlParameterSource.class));
    }

    @Test
    void rotateDeviceTokenAsync_nonActiveEnrollmentConflict() {
        DeviceEnrollment enrollment = enrollment(23L, "tenant-a", "ENR-23", "DE_ENROLLED", 10L);
        when(enrollmentRepository.findByIdAndTenant(23L, "tenant-a")).thenReturn(Optional.of(enrollment));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                service.rotateDeviceTokenAsync("tenant-a", "actor", 10L, 23L).block()
        );

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void rotateDeviceTokenAsync_revokesExistingAndIssuesNewToken() {
        DeviceEnrollment enrollment = enrollment(24L, "tenant-a", "ENR-24", "ACTIVE", 10L);
        DeviceAgentCredential existing = activeCredential(101L, "tenant-a", 24L, OffsetDateTime.now().plusDays(1));
        when(enrollmentRepository.findByIdAndTenant(24L, "tenant-a")).thenReturn(Optional.of(enrollment));
        when(credentialRepository.findActiveByEnrollmentId(24L)).thenReturn(List.of(existing));
        when(credentialRepository.save(any(DeviceAgentCredential.class))).thenAnswer(invocation -> {
            DeviceAgentCredential saved = invocation.getArgument(0);
            if (saved.getId() == null && "ACTIVE".equals(saved.getStatus())) {
                saved.setId(202L);
            }
            return saved;
        });

        DeviceEnrollmentService.DeviceTokenRotation rotation = service
                .rotateDeviceTokenAsync("tenant-a", "admin", 10L, 24L)
                .block();

        assertNotNull(rotation);
        assertEquals(24L, rotation.enrollmentId());
        assertTrue(rotation.deviceToken().startsWith("dvt_"));
        assertNotNull(rotation.deviceTokenExpiresAt());
        assertEquals("REVOKED", existing.getStatus());
        assertEquals("admin", existing.getRevokedBy());
    }

    @Test
    void claimWithSetupKeyAsync_invalidSetupKeyRejected() {
        when(setupKeyRepository.findActiveByHash(anyString())).thenReturn(List.of());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                service.claimWithSetupKeyAsync("ABC-DEF-GHI-JKL", "agent-1", "fingerprint", "Phone").block()
        );

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
    }

    @Test
    void claimWithSetupKeyAsync_expiredKeyIsMarkedAndRejected() {
        DeviceSetupKey key = new DeviceSetupKey();
        key.setId(501L);
        key.setTenantId("tenant-a");
        key.setTargetUserId(2L);
        key.setMaxUses(5);
        key.setUsedCount(0);
        key.setStatus("ACTIVE");
        key.setExpiresAt(OffsetDateTime.now().minusMinutes(1));
        when(setupKeyRepository.findActiveByHash(anyString())).thenReturn(List.of(key));
        when(tenantRepository.findActiveByTenantId("tenant-a")).thenReturn(Optional.of(tenant(99L, "tenant-a", "ACTIVE")));
        when(authUserRepository.findActiveByIdAndTenantId(2L, 99L)).thenReturn(Optional.of(user(2L, 99L, "TENANT_USER")));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                service.claimWithSetupKeyAsync("ABC-DEF-GHI-JKL", "agent-1", null, null).block()
        );

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        assertEquals("EXPIRED", key.getStatus());
        verify(setupKeyRepository, times(1)).save(key);
    }

    @Test
    void claimWithSetupKeyAsync_claimUpdateMismatchRejected() {
        DeviceSetupKey key = new DeviceSetupKey();
        key.setId(502L);
        key.setTenantId("tenant-a");
        key.setTargetUserId(2L);
        key.setMaxUses(5);
        key.setUsedCount(0);
        key.setStatus("ACTIVE");
        key.setExpiresAt(OffsetDateTime.now().plusMinutes(30));
        when(setupKeyRepository.findActiveByHash(anyString())).thenReturn(List.of(key));
        when(tenantRepository.findActiveByTenantId("tenant-a")).thenReturn(Optional.of(tenant(99L, "tenant-a", "ACTIVE")));
        when(authUserRepository.findActiveByIdAndTenantId(2L, 99L)).thenReturn(Optional.of(user(2L, 99L, "TENANT_USER")));
        when(jdbc.update(contains("UPDATE device_setup_key"), any(org.springframework.jdbc.core.namedparam.MapSqlParameterSource.class)))
                .thenReturn(0);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                service.claimWithSetupKeyAsync("ABC-DEF-GHI-JKL", "agent-1", null, null).block()
        );

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
    }

    @Test
    void claimWithSetupKeyAsync_successCreatesEnrollmentAndCredential() {
        DeviceSetupKey key = new DeviceSetupKey();
        key.setId(503L);
        key.setTenantId("tenant-a");
        key.setTargetUserId(2L);
        key.setMaxUses(5);
        key.setUsedCount(0);
        key.setStatus("ACTIVE");
        key.setExpiresAt(OffsetDateTime.now().plusMinutes(30));
        when(setupKeyRepository.findActiveByHash(anyString())).thenReturn(List.of(key));
        when(tenantRepository.findActiveByTenantId("tenant-a")).thenReturn(Optional.of(tenant(99L, "tenant-a", "ACTIVE")));
        when(authUserRepository.findActiveByIdAndTenantId(2L, 99L)).thenReturn(Optional.of(user(2L, 99L, "TENANT_USER")));
        when(jdbc.update(contains("UPDATE device_setup_key"), any(org.springframework.jdbc.core.namedparam.MapSqlParameterSource.class)))
                .thenReturn(1);
        when(enrollmentRepository.findByTenantAndEnrollmentNo(eq("tenant-a"), anyString())).thenReturn(Optional.empty());
        when(enrollmentRepository.save(any(DeviceEnrollment.class))).thenAnswer(invocation -> {
            DeviceEnrollment saved = invocation.getArgument(0);
            saved.setId(700L);
            return saved;
        });
        when(credentialRepository.save(any(DeviceAgentCredential.class))).thenAnswer(invocation -> {
            DeviceAgentCredential saved = invocation.getArgument(0);
            saved.setId(701L);
            return saved;
        });

        DeviceEnrollmentService.AgentEnrollmentClaim claim = service
                .claimWithSetupKeyAsync("ABC-DEF-GHI-JKL", "agent-xyz", "fp-1", "Pixel")
                .block();

        assertNotNull(claim);
        assertTrue(claim.enrollmentNo().startsWith("ENR-"));
        assertTrue(claim.deviceToken().startsWith("dvt_"));
        assertNotNull(claim.tokenHint());
        assertNotNull(claim.deviceTokenExpiresAt());
    }

    @Test
    void authenticateDeviceTokenAsync_expiredCredentialRevokedAndRejected() {
        DeviceAgentCredential credential = activeCredential(800L, "tenant-a", 900L, OffsetDateTime.now().minusMinutes(1));
        when(credentialRepository.findActiveByTokenHash(anyString())).thenReturn(Optional.of(credential));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                service.authenticateDeviceTokenAsync("raw-token").block()
        );

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        assertEquals("EXPIRED", credential.getStatus());
        assertEquals("device-token-expiry", credential.getRevokedBy());
    }

    @Test
    void authenticateDeviceTokenAsync_successReturnsPrincipal() {
        DeviceAgentCredential credential = activeCredential(801L, "tenant-a", 901L, OffsetDateTime.now().plusHours(2));
        DeviceEnrollment enrollment = enrollment(901L, "tenant-a", "ENR-901", "ACTIVE", 10L);
        when(credentialRepository.findActiveByTokenHash(anyString())).thenReturn(Optional.of(credential));
        when(enrollmentRepository.findById(901L)).thenReturn(Optional.of(enrollment));

        DeviceEnrollmentService.DeviceTokenPrincipal principal = service.authenticateDeviceTokenAsync("raw-token").block();

        assertNotNull(principal);
        assertEquals("tenant-a", principal.tenantId());
        assertEquals("ENR-901", principal.enrollmentNo());
        assertEquals(901L, principal.enrollmentId());
    }

    @Test
    void ensureActiveEnrollmentAsync_conflictWhenEnrollmentNotActive() {
        when(enrollmentRepository.findActiveByTenantAndEnrollmentNo("tenant-a", "ENR-NA")).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                service.ensureActiveEnrollmentAsync("tenant-a", "ENR-NA").block()
        );

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void normalizeSetupKeyInput_acceptsCompactAndGroupedFormats() {
        when(setupKeyRepository.findActiveByHash(anyString())).thenReturn(List.of());

        assertThrows(ResponseStatusException.class, () ->
                service.claimWithQrAsync("abcdefghijkl", "agent", null, null).block()
        );
        assertThrows(ResponseStatusException.class, () ->
                service.claimWithQrAsync("abc-def-ghi-jkl", "agent", null, null).block()
        );
    }

    @Test
    void createSetupKeyAsync_rejectsProductAdminAsTarget() {
        Tenant tenant = tenant(99L, "tenant-a", "ACTIVE");
        when(tenantRepository.findActiveByTenantId("tenant-a")).thenReturn(Optional.of(tenant));
        when(authUserRepository.findActiveByIdAndTenantId(1L, 99L)).thenReturn(Optional.of(user(1L, 99L, "TENANT_ADMIN")));
        when(authUserRepository.findActiveByIdAndTenantId(2L, 99L)).thenReturn(Optional.of(user(2L, 99L, "PRODUCT_ADMIN")));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                service.createSetupKeyAsync("tenant-a", 1L, 2L, "actor", 10, 30).block()
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void authenticateDeviceTokenAsync_tenantMismatchRejected() {
        DeviceAgentCredential credential = activeCredential(802L, "tenant-b", 902L, OffsetDateTime.now().plusMinutes(30));
        DeviceEnrollment enrollment = enrollment(902L, "tenant-a", "ENR-902", "ACTIVE", 10L);
        when(credentialRepository.findActiveByTokenHash(anyString())).thenReturn(Optional.of(credential));
        when(enrollmentRepository.findById(902L)).thenReturn(Optional.of(enrollment));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                service.authenticateDeviceTokenAsync("raw-token").block()
        );
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
    }

    @Test
    void deEnrollAsync_truncatesLongReasonToAllowedLength() {
        DeviceEnrollment enrollment = enrollment(25L, "tenant-a", "ENR-25", "ACTIVE", 10L);
        when(enrollmentRepository.findByIdAndTenant(25L, "tenant-a")).thenReturn(Optional.of(enrollment));
        when(credentialRepository.findActiveByEnrollmentId(25L)).thenReturn(List.of());
        when(jdbc.update(anyString(), any(org.springframework.jdbc.core.namedparam.MapSqlParameterSource.class))).thenReturn(1);
        String longReason = "x".repeat(2000);

        service.deEnrollAsync("tenant-a", "actor", 10L, 25L, longReason).block();

        assertEquals(1000, enrollment.getDeEnrollReason().length());
    }

    @Test
    void claimWithSetupKeyAsync_setsEnrollmentAndCredentialOwnerFromTargetUser() {
        DeviceSetupKey key = new DeviceSetupKey();
        key.setId(504L);
        key.setTenantId("tenant-a");
        key.setTargetUserId(42L);
        key.setMaxUses(10);
        key.setUsedCount(0);
        key.setStatus("ACTIVE");
        key.setExpiresAt(OffsetDateTime.now().plusMinutes(30));

        when(setupKeyRepository.findActiveByHash(anyString())).thenReturn(List.of(key));
        when(tenantRepository.findActiveByTenantId("tenant-a")).thenReturn(Optional.of(tenant(99L, "tenant-a", "ACTIVE")));
        when(authUserRepository.findActiveByIdAndTenantId(42L, 99L)).thenReturn(Optional.of(user(42L, 99L, "TENANT_USER")));
        when(jdbc.update(contains("UPDATE device_setup_key"), any(org.springframework.jdbc.core.namedparam.MapSqlParameterSource.class))).thenReturn(1);
        when(enrollmentRepository.findByTenantAndEnrollmentNo(eq("tenant-a"), anyString())).thenReturn(Optional.empty());
        when(enrollmentRepository.save(any(DeviceEnrollment.class))).thenAnswer(invocation -> {
            DeviceEnrollment saved = invocation.getArgument(0);
            saved.setId(710L);
            return saved;
        });

        service.claimWithSetupKeyAsync("ABC-DEF-GHI-JKL", "agent-99", null, null).block();

        ArgumentCaptor<DeviceEnrollment> enrollmentCaptor = ArgumentCaptor.forClass(DeviceEnrollment.class);
        verify(enrollmentRepository, times(1)).save(enrollmentCaptor.capture());
        assertEquals(42L, enrollmentCaptor.getValue().getOwnerUserId());
    }

    private Tenant tenant(Long id, String tenantId, String status) {
        Tenant tenant = new Tenant();
        tenant.setId(id);
        tenant.setTenantId(tenantId);
        tenant.setStatus(status);
        tenant.setDeleted(false);
        return tenant;
    }

    private AuthUser user(Long id, Long tenantId, String role) {
        AuthUser user = new AuthUser();
        user.setId(id);
        user.setTenantId(tenantId);
        user.setRole(role);
        user.setStatus("ACTIVE");
        user.setDeleted(false);
        return user;
    }

    private DeviceEnrollment enrollment(Long id, String tenantId, String enrollmentNo, String status, Long ownerUserId) {
        DeviceEnrollment enrollment = new DeviceEnrollment();
        enrollment.setId(id);
        enrollment.setTenantId(tenantId);
        enrollment.setEnrollmentNo(enrollmentNo);
        enrollment.setStatus(status);
        enrollment.setOwnerUserId(ownerUserId);
        return enrollment;
    }

    private DeviceAgentCredential activeCredential(Long id, String tenantId, Long enrollmentId, OffsetDateTime expiresAt) {
        DeviceAgentCredential credential = new DeviceAgentCredential();
        credential.setId(id);
        credential.setTenantId(tenantId);
        credential.setDeviceEnrollmentId(enrollmentId);
        credential.setStatus("ACTIVE");
        credential.setExpiresAt(expiresAt);
        credential.setTokenHash("hash-" + id);
        return credential;
    }
}
