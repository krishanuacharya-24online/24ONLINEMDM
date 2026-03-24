package com.e24online.mdm.web;

import com.e24online.mdm.records.tenant.SubscriptionPlanAdminResponse;
import com.e24online.mdm.records.tenant.SubscriptionPlanResponse;
import com.e24online.mdm.records.tenant.SubscriptionPlanUpsertRequest;
import com.e24online.mdm.service.TenantAdminService;
import com.e24online.mdm.service.TenantEntitlementService;
import com.e24online.mdm.service.TenantSubscriptionService;
import com.e24online.mdm.web.security.AuthenticatedRequestContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantAdminControllerTest {

    @Mock
    private TenantAdminService tenantAdminService;
    @Mock
    private TenantSubscriptionService tenantSubscriptionService;
    @Mock
    private TenantEntitlementService tenantEntitlementService;
    @Mock
    private AuthenticatedRequestContext requestContext;
    @Mock
    private Authentication authentication;

    private TenantAdminController controller;

    @BeforeEach
    void setUp() {
        controller = new TenantAdminController(
                tenantAdminService,
                tenantSubscriptionService,
                tenantEntitlementService,
                requestContext
        );
    }

    @Test
    void listAndManageSubscriptionPlans_delegateToService() {
        SubscriptionPlanResponse activePlan = new SubscriptionPlanResponse(
                "TRIAL", "Trial", "Default trial", 25, 10, 5000L, 30, false, false
        );
        SubscriptionPlanAdminResponse adminPlan = new SubscriptionPlanAdminResponse(
                7L, "STANDARD", "Standard", "Core plan", 50, 20, 10000L, 45,
                true, false, "ACTIVE", OffsetDateTime.now().minusDays(1), OffsetDateTime.now()
        );
        SubscriptionPlanUpsertRequest request = new SubscriptionPlanUpsertRequest(
                "STANDARD", "Standard", "Core plan", 50, 20, 10000L, 45, true, false, "ACTIVE"
        );

        when(requestContext.resolveActor(authentication)).thenReturn("admin");
        when(tenantSubscriptionService.listPlans()).thenReturn(Flux.just(activePlan));
        when(tenantSubscriptionService.listPlanCatalog()).thenReturn(Flux.just(adminPlan));
        when(tenantSubscriptionService.createPlan("admin", request)).thenReturn(Mono.just(adminPlan));
        when(tenantSubscriptionService.updatePlan(7L, "admin", request)).thenReturn(Mono.just(adminPlan));
        when(tenantSubscriptionService.retirePlan(7L, "admin")).thenReturn(Mono.just(
                new SubscriptionPlanAdminResponse(
                        7L, "STANDARD", "Standard", "Core plan", 50, 20, 10000L, 45,
                        true, false, "INACTIVE", adminPlan.createdAt(), OffsetDateTime.now()
                )
        ));

        List<SubscriptionPlanResponse> activePlans = controller.listSubscriptionPlans().collectList().block();
        List<SubscriptionPlanAdminResponse> catalog = controller.listSubscriptionPlanCatalog().collectList().block();
        SubscriptionPlanAdminResponse created = controller.createSubscriptionPlan(authentication, Mono.just(request)).block();
        SubscriptionPlanAdminResponse updated = controller.updateSubscriptionPlan(authentication, 7L, Mono.just(request)).block();
        SubscriptionPlanAdminResponse retired = controller.retireSubscriptionPlan(authentication, 7L).block();

        assertNotNull(activePlans);
        assertEquals(1, activePlans.size());
        assertEquals("TRIAL", activePlans.getFirst().planCode());
        assertNotNull(catalog);
        assertEquals(1, catalog.size());
        assertEquals("STANDARD", catalog.getFirst().planCode());
        assertEquals("STANDARD", created.planCode());
        assertEquals("ACTIVE", updated.status());
        assertEquals("INACTIVE", retired.status());

        verify(tenantSubscriptionService).listPlans();
        verify(tenantSubscriptionService).listPlanCatalog();
        verify(tenantSubscriptionService).createPlan("admin", request);
        verify(tenantSubscriptionService).updatePlan(7L, "admin", request);
        verify(tenantSubscriptionService).retirePlan(7L, "admin");
    }
}
