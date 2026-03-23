package com.e24online.mdm.service;

import com.e24online.mdm.domain.AuthRefreshToken;
import com.e24online.mdm.domain.AuthUser;
import com.e24online.mdm.domain.Tenant;
import com.e24online.mdm.records.user.BulkUserTokenInvalidationResponse;
import com.e24online.mdm.records.user.UserPrincipal;
import com.e24online.mdm.records.user.UserResponse;
import com.e24online.mdm.repository.AuthRefreshTokenRepository;
import com.e24online.mdm.repository.AuthUserRepository;
import com.e24online.mdm.repository.TenantRepository;
import com.e24online.mdm.service.LocalBreachedPasswordService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class UserAdminServiceTest {

    @Mock
    private AuthUserRepository authUserRepository;

    @Mock
    private AuthRefreshTokenRepository authRefreshTokenRepository;

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuditEventService auditEventService;

    @Mock
    private LocalBreachedPasswordService localBreachedPasswordService;
    @Mock
    private TenantEntitlementService tenantEntitlementService;

    private UserAdminService service;

    @BeforeEach
    void setUp() {
        service = new UserAdminService(
                authUserRepository,
                authRefreshTokenRepository,
                tenantRepository,
                passwordEncoder,
                new BlockingDb(Schedulers.immediate()),
                auditEventService,
                localBreachedPasswordService,
                tenantEntitlementService
        );
        lenient().when(authUserRepository.save(any(AuthUser.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(tenantRepository.findById(anyLong())).thenReturn(Optional.empty());
        lenient().when(localBreachedPasswordService.isBreached(any())).thenReturn(false);
    }

    @Test
    void updateUser_roleOrPasswordChangeInvalidatesActiveSessions() {
        AuthUser existing = new AuthUser();
        existing.setId(10L);
        existing.setUsername("alice");
        existing.setRole("TENANT_USER");
        existing.setStatus("ACTIVE");
        existing.setTenantId(1L);
        existing.setTokenVersion(2L);
        existing.setDeleted(false);
        when(authUserRepository.findById(10L)).thenReturn(Optional.of(existing));

        Tenant targetTenant = new Tenant();
        targetTenant.setId(2L);
        targetTenant.setTenantId("acme");
        targetTenant.setStatus("ACTIVE");
        when(tenantRepository.findActiveByTenantId("acme")).thenReturn(Optional.of(targetTenant));
        when(tenantRepository.findById(2L)).thenReturn(Optional.of(targetTenant));

        when(passwordEncoder.encode("NewStrongPass1!")).thenReturn("encoded-hash");

        AuthRefreshToken active = new AuthRefreshToken();
        active.setUserId(10L);
        active.setRevoked(false);
        AuthRefreshToken alreadyRevoked = new AuthRefreshToken();
        alreadyRevoked.setUserId(10L);
        alreadyRevoked.setRevoked(true);
        when(authRefreshTokenRepository.findByUserId(10L)).thenReturn(List.of(active, alreadyRevoked));

        UserResponse response = service.updateUser(
                10L,
                new UserPrincipal(1L, "root", "PRODUCT_ADMIN", null),
                "TENANT_ADMIN",
                "ACTIVE",
                "acme",
                "NewStrongPass1!"
        ).block();

        assertNotNull(response);
        assertEquals(3L, existing.getTokenVersion());
        assertEquals("TENANT_ADMIN", existing.getRole());
        assertEquals("encoded-hash", existing.getPasswordHash());
        verify(authRefreshTokenRepository, times(1)).save(active);
        verify(authRefreshTokenRepository, never()).save(alreadyRevoked);
    }

    @Test
    void updateUser_withoutSecurityChangesDoesNotInvalidateSessions() {
        AuthUser existing = new AuthUser();
        existing.setId(20L);
        existing.setUsername("bob");
        existing.setRole("TENANT_USER");
        existing.setStatus("ACTIVE");
        existing.setTenantId(7L);
        existing.setTokenVersion(5L);
        existing.setDeleted(false);
        when(authUserRepository.findById(20L)).thenReturn(Optional.of(existing));

        Tenant tenant = new Tenant();
        tenant.setId(7L);
        tenant.setTenantId("tenant-7");
        tenant.setStatus("ACTIVE");
        when(tenantRepository.findById(7L)).thenReturn(Optional.of(tenant));

        UserResponse response = service.updateUser(
                20L,
                new UserPrincipal(2L, "tenant-admin", "TENANT_ADMIN", 7L),
                "TENANT_USER",
                "ACTIVE",
                null,
                null
        ).block();

        assertNotNull(response);
        assertEquals(5L, existing.getTokenVersion());
        verify(authRefreshTokenRepository, never()).findByUserId(anyLong());
    }

    @Test
    void deleteUser_invalidatesSessions() {
        AuthUser existing = new AuthUser();
        existing.setId(30L);
        existing.setUsername("charlie");
        existing.setRole("TENANT_USER");
        existing.setStatus("ACTIVE");
        existing.setTenantId(3L);
        existing.setTokenVersion(null);
        existing.setDeleted(false);
        when(authUserRepository.findById(30L)).thenReturn(Optional.of(existing));

        AuthRefreshToken active = new AuthRefreshToken();
        active.setUserId(30L);
        active.setRevoked(false);
        when(authRefreshTokenRepository.findByUserId(30L)).thenReturn(List.of(active));

        service.deleteUser(30L, new UserPrincipal(99L, "tenant-admin", "TENANT_ADMIN", 3L)).block();

        assertTrue(existing.isDeleted());
        assertEquals("INACTIVE", existing.getStatus());
        assertEquals(1L, existing.getTokenVersion());
        verify(authRefreshTokenRepository, times(1)).save(eq(active));
    }

    @Test
    void listUsers_productAdmin_appliesTenantAndRoleFilters() {
        Tenant tenant = new Tenant();
        tenant.setId(11L);
        tenant.setTenantId("acme");
        tenant.setStatus("ACTIVE");
        when(tenantRepository.findActiveByTenantId("acme")).thenReturn(Optional.of(tenant));
        when(tenantRepository.findById(11L)).thenReturn(Optional.of(tenant));

        AuthUser user = new AuthUser();
        user.setId(91L);
        user.setUsername("tenant-user");
        user.setRole("TENANT_USER");
        user.setStatus("ACTIVE");
        user.setTenantId(11L);
        user.setDeleted(false);

        when(authUserRepository.findPaged("TENANT_USER", "ACTIVE", 11L, 50, 0L))
                .thenReturn(List.of(user));

        List<UserResponse> results = service.listUsers(
                new UserPrincipal(1L, "root", "PRODUCT_ADMIN", null),
                "TENANT_USER",
                "ACTIVE",
                "acme",
                0,
                50
        ).collectList().block();

        assertNotNull(results);
        assertEquals(1, results.size());
        assertEquals("tenant-user", results.getFirst().username());
        assertEquals("acme", results.getFirst().tenantId());
    }

    @Test
    void listUsers_tenantAdmin_defaultsToOwnTenantTenantUsers() {
        Tenant tenant = new Tenant();
        tenant.setId(7L);
        tenant.setTenantId("tenant-7");
        tenant.setStatus("ACTIVE");
        when(tenantRepository.findById(7L)).thenReturn(Optional.of(tenant));

        AuthUser user = new AuthUser();
        user.setId(92L);
        user.setUsername("tenant-member");
        user.setRole("TENANT_USER");
        user.setStatus("ACTIVE");
        user.setTenantId(7L);
        user.setDeleted(false);
        when(authUserRepository.findPaged("TENANT_USER", null, 7L, 50, 0L))
                .thenReturn(List.of(user));

        List<UserResponse> results = service.listUsers(
                new UserPrincipal(2L, "tenant-admin", "TENANT_ADMIN", 7L),
                null,
                null,
                null,
                0,
                50
        ).collectList().block();

        assertNotNull(results);
        assertEquals(1, results.size());
        verify(authUserRepository, times(1)).findPaged("TENANT_USER", null, 7L, 50, 0L);
    }

    @Test
    void getUser_tenantAdminCannotReadTenantAdminAccount() {
        AuthUser existing = new AuthUser();
        existing.setId(40L);
        existing.setUsername("tenant-admin-2");
        existing.setRole("TENANT_ADMIN");
        existing.setStatus("ACTIVE");
        existing.setTenantId(7L);
        existing.setDeleted(false);
        when(authUserRepository.findById(40L)).thenReturn(Optional.of(existing));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                service.getUser(40L, new UserPrincipal(2L, "tenant-admin", "TENANT_ADMIN", 7L)).block()
        );
        assertEquals(403, ex.getStatusCode().value());
    }

    @Test
    void createUser_activeTenantScopedUserChecksEntitlements() {
        Tenant tenant = new Tenant();
        tenant.setId(11L);
        tenant.setTenantId("acme");
        tenant.setStatus("ACTIVE");
        when(authUserRepository.findByUsernameAndIsDeletedFalse("alice")).thenReturn(Optional.empty());
        when(tenantRepository.findActiveByTenantId("acme")).thenReturn(Optional.of(tenant));
        when(tenantRepository.findById(11L)).thenReturn(Optional.of(tenant));
        when(passwordEncoder.encode("StrongPassword1!")).thenReturn("encoded");

        UserResponse response = service.createUser(
                new UserPrincipal(1L, "root", "PRODUCT_ADMIN", null),
                "alice",
                "StrongPassword1!",
                "TENANT_USER",
                "ACTIVE",
                "acme"
        ).block();

        assertNotNull(response);
        verify(tenantEntitlementService, times(1)).assertCanCreateActiveTenantUser(11L);
        verify(tenantEntitlementService, times(1)).refreshUsageSnapshotForTenantId(11L);
    }

    @Test
    void invalidateAllTokens_productAdmin_incrementsTokenVersionAndRevokesRefreshTokens() {
        AuthUser existing = new AuthUser();
        existing.setId(44L);
        existing.setUsername("tenant-user");
        existing.setRole("TENANT_USER");
        existing.setStatus("ACTIVE");
        existing.setTenantId(11L);
        existing.setTokenVersion(7L);
        existing.setDeleted(false);
        when(authUserRepository.findById(44L)).thenReturn(Optional.of(existing));

        Tenant tenant = new Tenant();
        tenant.setId(11L);
        tenant.setTenantId("acme");
        tenant.setStatus("ACTIVE");
        when(tenantRepository.findById(11L)).thenReturn(Optional.of(tenant));

        AuthRefreshToken active = new AuthRefreshToken();
        active.setUserId(44L);
        active.setRevoked(false);
        AuthRefreshToken alreadyRevoked = new AuthRefreshToken();
        alreadyRevoked.setUserId(44L);
        alreadyRevoked.setRevoked(true);
        when(authRefreshTokenRepository.findByUserId(44L)).thenReturn(List.of(active, alreadyRevoked));

        UserResponse response = service.invalidateAllTokens(
                44L,
                new UserPrincipal(1L, "root", "PRODUCT_ADMIN", null)
        ).block();

        assertNotNull(response);
        assertEquals(8L, existing.getTokenVersion());
        verify(authRefreshTokenRepository, times(1)).save(active);
        verify(authRefreshTokenRepository, never()).save(alreadyRevoked);
    }

    @Test
    void invalidateAllTokens_rejectsProtectedAdminUser() {
        AuthUser existing = new AuthUser();
        existing.setId(45L);
        existing.setUsername("admin");
        existing.setRole("PRODUCT_ADMIN");
        existing.setStatus("ACTIVE");
        existing.setTokenVersion(2L);
        existing.setDeleted(false);
        when(authUserRepository.findById(45L)).thenReturn(Optional.of(existing));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                service.invalidateAllTokens(45L, new UserPrincipal(1L, "root", "PRODUCT_ADMIN", null)).block()
        );

        assertEquals(403, ex.getStatusCode().value());
        verify(authRefreshTokenRepository, never()).findByUserId(anyLong());
    }

    @Test
    void invalidateAllTokens_rejectsTenantAdminActor() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                service.invalidateAllTokens(46L, new UserPrincipal(2L, "tenant-admin", "TENANT_ADMIN", 7L)).block()
        );

        assertEquals(403, ex.getStatusCode().value());
        verify(authUserRepository, never()).findById(anyLong());
    }

    @Test
    void invalidateAllTokensForAllUsers_productAdmin_skipsProtectedAdmin() {
        AuthUser protectedAdmin = new AuthUser();
        protectedAdmin.setId(1L);
        protectedAdmin.setUsername("admin");
        protectedAdmin.setRole("PRODUCT_ADMIN");
        protectedAdmin.setStatus("ACTIVE");
        protectedAdmin.setTokenVersion(9L);
        protectedAdmin.setDeleted(false);

        AuthUser tenantUser = new AuthUser();
        tenantUser.setId(2L);
        tenantUser.setUsername("alice");
        tenantUser.setRole("TENANT_USER");
        tenantUser.setStatus("ACTIVE");
        tenantUser.setTokenVersion(3L);
        tenantUser.setDeleted(false);

        AuthUser productAdmin = new AuthUser();
        productAdmin.setId(3L);
        productAdmin.setUsername("ops-admin");
        productAdmin.setRole("PRODUCT_ADMIN");
        productAdmin.setStatus("ACTIVE");
        productAdmin.setTokenVersion(null);
        productAdmin.setDeleted(false);

        when(authUserRepository.findAll()).thenReturn(List.of(protectedAdmin, tenantUser, productAdmin));

        AuthRefreshToken tenantToken = new AuthRefreshToken();
        tenantToken.setUserId(2L);
        tenantToken.setRevoked(false);
        AuthRefreshToken productToken = new AuthRefreshToken();
        productToken.setUserId(3L);
        productToken.setRevoked(false);
        when(authRefreshTokenRepository.findByUserId(2L)).thenReturn(List.of(tenantToken));
        when(authRefreshTokenRepository.findByUserId(3L)).thenReturn(List.of(productToken));

        BulkUserTokenInvalidationResponse response = service.invalidateAllTokensForAllUsers(
                new UserPrincipal(1L, "admin", "PRODUCT_ADMIN", null)
        ).block();

        assertNotNull(response);
        assertEquals(2L, response.invalidatedUserCount());
        assertEquals(1L, response.skippedProtectedUserCount());
        assertEquals(2L, response.revokedRefreshTokenCount());
        assertEquals(9L, protectedAdmin.getTokenVersion());
        assertEquals(4L, tenantUser.getTokenVersion());
        assertEquals(1L, productAdmin.getTokenVersion());
        verify(authUserRepository, times(2)).save(any(AuthUser.class));
        verify(authRefreshTokenRepository, times(1)).save(tenantToken);
        verify(authRefreshTokenRepository, times(1)).save(productToken);
    }

    @Test
    void invalidateAllTokensForAllUsers_rejectsTenantAdminActor() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                service.invalidateAllTokensForAllUsers(new UserPrincipal(2L, "tenant-admin", "TENANT_ADMIN", 7L)).block()
        );

        assertEquals(403, ex.getStatusCode().value());
        verify(authUserRepository, never()).findAll();
    }
}
