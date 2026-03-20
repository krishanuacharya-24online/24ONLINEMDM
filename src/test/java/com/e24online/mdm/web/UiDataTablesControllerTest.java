package com.e24online.mdm.web;

import com.e24online.mdm.records.ui.DataTablePage;
import com.e24online.mdm.records.ui.DataTableResponse;
import com.e24online.mdm.records.user.UserPrincipal;
import com.e24online.mdm.service.BlockingDb;
import com.e24online.mdm.service.UiDataTableService;
import com.e24online.mdm.web.security.AuthenticatedRequestContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UiDataTablesControllerTest {

    @Mock
    private UiDataTableService dataTableService;

    @Mock
    private AuthenticatedRequestContext requestContext;

    @Mock
    private Authentication authentication;

    private UiDataTablesController controller;

    @BeforeEach
    void setUp() {
        controller = new UiDataTablesController(
                dataTableService,
                new BlockingDb(Schedulers.immediate()),
                requestContext
        );
    }

    @Test
    void basicDataTableEndpoints_delegateAndWrapResponses() {
        DataTablePage page = samplePage(5);
        when(requestContext.resolveOptionalTenantId(authentication, null)).thenReturn(Mono.empty());
        when(dataTableService.systemRules(1, 0, 25, null, "ACTIVE", "q", "name", "asc")).thenReturn(page);
        when(dataTableService.rejectApps(1, 0, 25, null, "WINDOWS", "ACTIVE", "q", "name", "asc")).thenReturn(page);
        when(dataTableService.trustScorePolicies(1, 0, 25, null, "ACTIVE", "q", "name", "asc")).thenReturn(page);
        when(dataTableService.trustDecisionPolicies(1, 0, 25, null, "ACTIVE", "q", "name", "asc")).thenReturn(page);
        when(dataTableService.remediationRules(1, 0, 25, null, "ACTIVE", "q", "name", "asc")).thenReturn(page);
        when(dataTableService.ruleRemediationMappings(1, 0, 25, null, "SYSTEM", "q", "name", "asc")).thenReturn(page);
        when(dataTableService.policyAudit(1, 0, 25, null, "SYSTEM_RULE", "CREATE", "admin", "q", "created_at", "desc")).thenReturn(page);
        when(dataTableService.auditEvents(1, 0, 25, null, "AUTH", "USER_LOGIN", "LOGIN", "SUCCESS", "admin", "q", "created_at", "desc")).thenReturn(page);
        when(dataTableService.catalogApplications(1, 0, 25, "WINDOWS", "q", "name", "asc")).thenReturn(page);
        when(dataTableService.osLifecycle(1, 0, 25, "WIN", "q", "name", "asc")).thenReturn(page);
        when(dataTableService.lookupValues(1, 0, 25, "platform", "q", "name", "asc")).thenReturn(page);
        when(dataTableService.systemRuleConditions(1, 0, 25, null, 99L, "q", "name", "asc")).thenReturn(page);
        when(dataTableService.tenants(1, 0, 25, "ACTIVE", "q", "name", "asc")).thenReturn(page);

        assertResponse(controller.systemRules(authentication, null, 1, 0, 25, "ACTIVE", "q", "name", "asc").block(), 5);
        assertResponse(controller.rejectApps(authentication, null, 1, 0, 25, "WINDOWS", "ACTIVE", "q", "name", "asc").block(), 5);
        assertResponse(controller.trustScorePolicies(authentication, null, 1, 0, 25, "ACTIVE", "q", "name", "asc").block(), 5);
        assertResponse(controller.trustDecisionPolicies(authentication, null, 1, 0, 25, "ACTIVE", "q", "name", "asc").block(), 5);
        assertResponse(controller.remediationRules(authentication, null, 1, 0, 25, "ACTIVE", "q", "name", "asc").block(), 5);
        assertResponse(controller.ruleRemediationMappings(authentication, null, 1, 0, 25, "SYSTEM", "q", "name", "asc").block(), 5);
        assertResponse(controller.policyAudit(authentication, null, 1, 0, 25, "SYSTEM_RULE", "CREATE", "admin", "q", "created_at", "desc").block(), 5);
        assertResponse(controller.auditEvents(authentication, null, 1, 0, 25, "AUTH", "USER_LOGIN", "LOGIN", "SUCCESS", "admin", "q", "created_at", "desc").block(), 5);
        assertResponse(controller.catalogApplications(1, 0, 25, "WINDOWS", "q", "name", "asc").block(), 5);
        assertResponse(controller.osLifecycle(1, 0, 25, "WIN", "q", "name", "asc").block(), 5);
        assertResponse(controller.lookupValues(1, 0, 25, "platform", "q", "name", "asc").block(), 5);
        assertResponse(controller.systemRuleConditions(authentication, null, 1, 0, 25, 99L, "q", "name", "asc").block(), 5);
        assertResponse(controller.tenants(1, 0, 25, "ACTIVE", "q", "name", "asc").block(), 5);
    }

    @Test
    void deviceTrustProfiles_productAdmin_doesNotNeedTenantResolution() {
        UserPrincipal principal = new UserPrincipal(11L, "admin", "PRODUCT_ADMIN", null);
        DataTablePage page = samplePage(7);
        when(requestContext.requireUserPrincipal(authentication)).thenReturn(principal);
        when(dataTableService.deviceTrustProfiles(2, 0, 25, null, null, "WINDOWS", "TRUSTED", "q", "name", "asc"))
                .thenReturn(page);

        DataTableResponse<Map<String, Object>> response = controller
                .deviceTrustProfiles(authentication, 2, 0, 25, "WINDOWS", "TRUSTED", "q", "name", "asc")
                .block();

        assertResponse(response, 7);
        verify(requestContext, never()).resolveTenantId(any(), any());
    }

    @Test
    void deviceTrustProfiles_tenantAdmin_usesResolvedTenant() {
        UserPrincipal principal = new UserPrincipal(12L, "tenant-admin", "TENANT_ADMIN", 22L);
        DataTablePage page = samplePage(8);
        when(requestContext.requireUserPrincipal(authentication)).thenReturn(principal);
        when(requestContext.resolveTenantId(authentication, null)).thenReturn(Mono.just("tenant-a"));
        when(dataTableService.deviceTrustProfiles(3, 0, 25, "tenant-a", null, "MACOS", "LOW_RISK", "q", "name", "desc"))
                .thenReturn(page);

        DataTableResponse<Map<String, Object>> response = controller
                .deviceTrustProfiles(authentication, 3, 0, 25, "MACOS", "LOW_RISK", "q", "name", "desc")
                .block();

        assertResponse(response, 8);
        verify(requestContext).resolveTenantId(authentication, null);
    }

    @Test
    void deviceTrustProfiles_tenantUser_scopesToOwner() {
        UserPrincipal principal = new UserPrincipal(99L, "user", "TENANT_USER", 22L);
        DataTablePage page = samplePage(9);
        when(requestContext.requireUserPrincipal(authentication)).thenReturn(principal);
        when(requestContext.resolveTenantId(authentication, null)).thenReturn(Mono.just("tenant-z"));
        when(dataTableService.deviceTrustProfiles(4, 0, 25, "tenant-z", 99L, "LINUX", "HIGH_RISK", "q", "name", "asc"))
                .thenReturn(page);

        DataTableResponse<Map<String, Object>> response = controller
                .deviceTrustProfiles(authentication, 4, 0, 25, "LINUX", "HIGH_RISK", "q", "name", "asc")
                .block();

        assertResponse(response, 9);
    }

    @Test
    void deviceTrustProfiles_tenantUserWithoutId_returnsBadRequest() {
        UserPrincipal principal = new UserPrincipal(null, "user", "TENANT_USER", 22L);
        when(requestContext.requireUserPrincipal(authentication)).thenReturn(principal);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> controller
                .deviceTrustProfiles(authentication, 4, 0, 25, "LINUX", "HIGH_RISK", "q", "name", "asc")
                .block());

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void deviceTrustProfiles_unsupportedRole_returnsForbidden() {
        UserPrincipal principal = new UserPrincipal(1L, "guest", "AUDITOR", null);
        when(requestContext.requireUserPrincipal(authentication)).thenReturn(principal);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> controller
                .deviceTrustProfiles(authentication, 4, 0, 25, "IOS", "LOW_RISK", "q", "name", "asc")
                .block());

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void posturePayloads_handlesRolesAndTenantResolution() {
        DataTablePage page = samplePage(10);
        UserPrincipal productAdmin = new UserPrincipal(10L, "padmin", "PRODUCT_ADMIN", null);
        when(requestContext.requireUserPrincipal(authentication)).thenReturn(productAdmin);
        when(dataTableService.posturePayloads(5, 0, 25, "tenant-a", "FAILED", "q", "created_at", "desc")).thenReturn(page);

        DataTableResponse<Map<String, Object>> productResponse = controller
                .posturePayloads(authentication, 5, 0, 25, "tenant-a", "FAILED", "q", "created_at", "desc")
                .block();
        assertResponse(productResponse, 10);

        UserPrincipal tenantAdmin = new UserPrincipal(20L, "tadmin", "TENANT_ADMIN", 30L);
        when(requestContext.requireUserPrincipal(authentication)).thenReturn(tenantAdmin);
        when(requestContext.resolveTenantId(authentication, "tenant-ignored")).thenReturn(Mono.just("tenant-b"));
        when(dataTableService.posturePayloads(6, 0, 25, "tenant-b", "QUEUED", "q", "created_at", "asc")).thenReturn(page);

        DataTableResponse<Map<String, Object>> tenantResponse = controller
                .posturePayloads(authentication, 6, 0, 25, "tenant-ignored", "QUEUED", "q", "created_at", "asc")
                .block();
        assertResponse(tenantResponse, 10);

        UserPrincipal unsupported = new UserPrincipal(30L, "u", "TENANT_USER", 30L);
        when(requestContext.requireUserPrincipal(authentication)).thenReturn(unsupported);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> controller
                .posturePayloads(authentication, 6, 0, 25, "tenant-b", "QUEUED", "q", "created_at", "asc")
                .block());
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void users_handlesRolesAndTenantScopeValidation() {
        DataTablePage page = samplePage(11);
        UserPrincipal productAdmin = new UserPrincipal(31L, "admin", "PRODUCT_ADMIN", null);
        when(requestContext.requireUserPrincipal(authentication)).thenReturn(productAdmin);
        when(dataTableService.users(7, 0, 25, "TENANT_ADMIN", "ACTIVE", null, false, "q", "username", "asc"))
                .thenReturn(page);

        DataTableResponse<Map<String, Object>> productResponse = controller
                .users(authentication, 7, 0, 25, "TENANT_ADMIN", "ACTIVE", "q", "username", "asc")
                .block();
        assertResponse(productResponse, 11);

        UserPrincipal tenantAdmin = new UserPrincipal(32L, "tadmin", "TENANT_ADMIN", 777L);
        when(requestContext.requireUserPrincipal(authentication)).thenReturn(tenantAdmin);
        when(dataTableService.users(8, 0, 25, "TENANT_USER", "ACTIVE", 777L, true, "q", "username", "desc"))
                .thenReturn(page);

        DataTableResponse<Map<String, Object>> tenantResponse = controller
                .users(authentication, 8, 0, 25, "TENANT_USER", "ACTIVE", "q", "username", "desc")
                .block();
        assertResponse(tenantResponse, 11);

        UserPrincipal tenantWithoutScope = new UserPrincipal(33L, "bad", "TENANT_ADMIN", null);
        when(requestContext.requireUserPrincipal(authentication)).thenReturn(tenantWithoutScope);
        ResponseStatusException missingScope = assertThrows(ResponseStatusException.class, () -> controller
                .users(authentication, 8, 0, 25, "TENANT_USER", "ACTIVE", "q", "username", "desc")
                .block());
        assertEquals(HttpStatus.FORBIDDEN, missingScope.getStatusCode());

        UserPrincipal unsupported = new UserPrincipal(34L, "guest", "TENANT_USER", 55L);
        when(requestContext.requireUserPrincipal(authentication)).thenReturn(unsupported);
        ResponseStatusException unsupportedRole = assertThrows(ResponseStatusException.class, () -> controller
                .users(authentication, 8, 0, 25, "TENANT_USER", "ACTIVE", "q", "username", "desc")
                .block());
        assertEquals(HttpStatus.FORBIDDEN, unsupportedRole.getStatusCode());
    }

    private DataTablePage samplePage(int draw) {
        return new DataTablePage(
                draw,
                100L,
                80L,
                List.of(Map.of("id", 1L, "name", "row"))
        );
    }

    private void assertResponse(DataTableResponse<Map<String, Object>> response, int expectedDraw) {
        assertNotNull(response);
        assertEquals(expectedDraw, response.draw());
        assertEquals(100L, response.recordsTotal());
        assertEquals(80L, response.recordsFiltered());
        assertEquals(1, response.data().size());
    }
}
