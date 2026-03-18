package com.e24online.mdm.web.security;

import com.e24online.mdm.domain.Tenant;
import com.e24online.mdm.records.user.UserPrincipal;
import com.e24online.mdm.repository.TenantRepository;
import com.e24online.mdm.service.BlockingDb;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.scheduler.Schedulers;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthenticatedRequestContextTest {

    @Mock
    private TenantRepository tenantRepository;

    private AuthenticatedRequestContext context;

    @BeforeEach
    void setUp() {
        context = new AuthenticatedRequestContext(
                tenantRepository,
                new BlockingDb(Schedulers.immediate())
        );
    }

    @Test
    void resolveActor_returnsTrimmedUsername() {
        Authentication auth = auth(new UserPrincipal(1L, "  alice  ", "TENANT_USER", 7L));

        String actor = context.resolveActor(auth);

        assertEquals("alice", actor);
    }

    @Test
    void resolveActor_returnsSystemWhenAuthenticationIsMissing() {
        assertEquals("system", context.resolveActor(null));
    }

    @Test
    void requireUserPrincipal_throwsUnauthorizedWhenPrincipalMissing() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> context.requireUserPrincipal(null));

        assertEquals(401, ex.getStatusCode().value());
    }

    @Test
    void resolveTenantId_productAdmin_requiresRequestedTenant() {
        Authentication auth = auth(new UserPrincipal(1L, "root", "PRODUCT_ADMIN", null));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> context.resolveTenantId(auth, null).block());

        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    void resolveTenantId_productAdmin_returnsNormalizedTenant() {
        Authentication auth = auth(new UserPrincipal(1L, "root", "PRODUCT_ADMIN", null));

        String tenantId = context.resolveTenantId(auth, "  TENANT_A  ").block();

        assertEquals("tenant_a", tenantId);
    }

    @Test
    void resolveTenantId_auditorRole_resolvesTenantScope() {
        Authentication auth = auth(new UserPrincipal(3L, "ops", "AUDITOR", 11L));
        when(tenantRepository.findById(11L)).thenReturn(Optional.of(activeTenant(11L, "tenant-a")));

        String tenantId = context.resolveTenantId(auth, "tenant-a").block();

        assertEquals("tenant-a", tenantId);
    }

    @Test
    void resolveTenantId_tenantRoleMissingTenantPk_isForbidden() {
        Authentication auth = auth(new UserPrincipal(7L, "tenant-user", "TENANT_USER", null));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> context.resolveTenantId(auth, "tenant-a").block());

        assertEquals(403, ex.getStatusCode().value());
    }

    @Test
    void resolveTenantId_tenantScopeMissingInRepository_isForbidden() {
        Authentication auth = auth(new UserPrincipal(7L, "tenant-user", "TENANT_USER", 99L));
        when(tenantRepository.findById(99L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> context.resolveTenantId(auth, "tenant-a").block());

        assertEquals(403, ex.getStatusCode().value());
    }

    @Test
    void resolveTenantId_tenantScopeMismatch_isForbidden() {
        Authentication auth = auth(new UserPrincipal(7L, "tenant-admin", "TENANT_ADMIN", 55L));
        when(tenantRepository.findById(55L)).thenReturn(Optional.of(activeTenant(55L, "tenant-a")));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> context.resolveTenantId(auth, "tenant-b").block());

        assertEquals(403, ex.getStatusCode().value());
    }

    @Test
    void resolveTenantId_tenantRoleReturnsEffectiveScopeWhenRequestMissing() {
        Authentication auth = auth(new UserPrincipal(7L, "tenant-admin", "TENANT_ADMIN", 55L));
        when(tenantRepository.findById(55L)).thenReturn(Optional.of(activeTenant(55L, "Tenant-A")));

        String tenantId = context.resolveTenantId(auth, null).block();

        assertEquals("tenant-a", tenantId);
    }

    @Test
    void resolveTenantId_tenantScopeInvalidTenantId_isForbidden() {
        Tenant tenant = activeTenant(55L, "   ");
        Authentication auth = auth(new UserPrincipal(7L, "tenant-admin", "TENANT_ADMIN", 55L));
        when(tenantRepository.findById(55L)).thenReturn(Optional.of(tenant));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> context.resolveTenantId(auth, null).block());

        assertEquals(403, ex.getStatusCode().value());
    }

    private Authentication auth(UserPrincipal principal) {
        return new UsernamePasswordAuthenticationToken(principal, null);
    }

    private Tenant activeTenant(Long id, String tenantId) {
        Tenant tenant = new Tenant();
        tenant.setId(id);
        tenant.setTenantId(tenantId);
        tenant.setStatus("ACTIVE");
        tenant.setDeleted(false);
        return tenant;
    }
}
