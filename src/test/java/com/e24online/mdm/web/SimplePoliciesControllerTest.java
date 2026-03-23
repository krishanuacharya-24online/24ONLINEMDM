package com.e24online.mdm.web;

import com.e24online.mdm.domain.RemediationRule;
import com.e24online.mdm.domain.TrustScoreDecisionPolicy;
import com.e24online.mdm.service.PoliciesCrudService;
import com.e24online.mdm.service.SimplePolicyService;
import com.e24online.mdm.service.SimplePolicySimulationService;
import com.e24online.mdm.web.dto.SimpleAppPolicyRequest;
import com.e24online.mdm.web.dto.SimpleAppPolicySummary;
import com.e24online.mdm.web.dto.SimpleDevicePolicyRequest;
import com.e24online.mdm.web.dto.SimpleDevicePolicySummary;
import com.e24online.mdm.web.dto.SimplePolicySimulationRequest;
import com.e24online.mdm.web.dto.SimplePolicySimulationResponse;
import com.e24online.mdm.web.dto.SimplePolicyStarterPackSummary;
import com.e24online.mdm.web.security.AuthenticatedRequestContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SimplePoliciesControllerTest {

    @Mock
    private SimplePolicyService simplePolicyService;

    @Mock
    private PoliciesCrudService policiesCrudService;

    @Mock
    private SimplePolicySimulationService simplePolicySimulationService;

    @Mock
    private AuthenticatedRequestContext requestContext;

    @Mock
    private Authentication authentication;

    private SimplePoliciesController controller;

    @BeforeEach
    void setUp() {
        controller = new SimplePoliciesController(policiesCrudService, simplePolicyService, simplePolicySimulationService, requestContext);
        lenient().when(requestContext.resolveActor(authentication)).thenReturn("actor");
        lenient().when(requestContext.resolveRole(authentication)).thenReturn("TENANT_ADMIN");
        lenient().when(requestContext.resolveOptionalTenantId(authentication, null)).thenReturn(Mono.just("tenant-a"));
    }

    @Test
    void allEndpoints_delegateResolvedScope() {
        SimpleDevicePolicySummary deviceSummary = new SimpleDevicePolicySummary();
        deviceSummary.setId(1L);
        SimpleAppPolicySummary appSummary = new SimpleAppPolicySummary();
        appSummary.setId(2L);
        SimplePolicyStarterPackSummary starterPackSummary = new SimplePolicyStarterPackSummary();
        starterPackSummary.setScope("tenant-a");
        SimplePolicySimulationResponse simulationResponse = new SimplePolicySimulationResponse();
        simulationResponse.setDecisionAction("QUARANTINE");
        TrustScoreDecisionPolicy trustLevel = new TrustScoreDecisionPolicy();
        trustLevel.setId(3L);
        RemediationRule remediationRule = new RemediationRule();
        remediationRule.setId(4L);

        when(simplePolicyService.listDevicePolicies("TENANT_ADMIN", "tenant-a")).thenReturn(Flux.just(deviceSummary));
        when(simplePolicyService.createDevicePolicy(eq("actor"), eq("TENANT_ADMIN"), eq("tenant-a"), any())).thenReturn(Mono.just(deviceSummary));
        when(simplePolicyService.updateDevicePolicy(eq("actor"), eq("TENANT_ADMIN"), eq("tenant-a"), eq(1L), any())).thenReturn(Mono.just(deviceSummary));
        when(simplePolicyService.deleteDevicePolicy("actor", "TENANT_ADMIN", "tenant-a", 1L)).thenReturn(Mono.empty());

        when(simplePolicyService.listAppPolicies("TENANT_ADMIN", "tenant-a")).thenReturn(Flux.just(appSummary));
        when(simplePolicyService.createAppPolicy(eq("actor"), eq("TENANT_ADMIN"), eq("tenant-a"), any())).thenReturn(Mono.just(appSummary));
        when(simplePolicyService.updateAppPolicy(eq("actor"), eq("TENANT_ADMIN"), eq("tenant-a"), eq(2L), any())).thenReturn(Mono.just(appSummary));
        when(simplePolicyService.deleteAppPolicy("actor", "TENANT_ADMIN", "tenant-a", 2L)).thenReturn(Mono.empty());
        when(simplePolicyService.installStarterPack("actor", "TENANT_ADMIN", "tenant-a")).thenReturn(Mono.just(starterPackSummary));
        when(simplePolicyService.listTrustLevels("TENANT_ADMIN", "tenant-a")).thenReturn(Flux.just(trustLevel));
        when(simplePolicyService.listFixLibrary("TENANT_ADMIN", "tenant-a")).thenReturn(Flux.just(remediationRule));
        when(simplePolicyService.listFixOptions("TENANT_ADMIN", "tenant-a")).thenReturn(Flux.just(remediationRule));
        when(policiesCrudService.createTrustDecisionPolicy(eq("actor"), eq("TENANT_ADMIN"), eq("tenant-a"), any())).thenReturn(Mono.just(trustLevel));
        when(policiesCrudService.updateTrustDecisionPolicy(eq("actor"), eq("TENANT_ADMIN"), eq("tenant-a"), eq(3L), any())).thenReturn(Mono.just(trustLevel));
        when(policiesCrudService.deleteTrustDecisionPolicy("actor", "TENANT_ADMIN", "tenant-a", 3L)).thenReturn(Mono.empty());
        when(policiesCrudService.createRemediationRule(eq("actor"), eq("TENANT_ADMIN"), eq("tenant-a"), any())).thenReturn(Mono.just(remediationRule));
        when(policiesCrudService.updateRemediationRule(eq("actor"), eq("TENANT_ADMIN"), eq("tenant-a"), eq(4L), any())).thenReturn(Mono.just(remediationRule));
        when(policiesCrudService.deleteRemediationRule("actor", "TENANT_ADMIN", "tenant-a", 4L)).thenReturn(Mono.empty());
        when(simplePolicySimulationService.simulate(eq("tenant-a"), any())).thenReturn(Mono.just(simulationResponse));

        assertEquals(1, controller.listDeviceChecks(authentication, null).collectList().block().size());
        assertNotNull(controller.createDeviceCheck(authentication, null, Mono.just(new SimpleDevicePolicyRequest())).block());
        assertNotNull(controller.updateDeviceCheck(authentication, null, 1L, Mono.just(new SimpleDevicePolicyRequest())).block());
        controller.deleteDeviceCheck(authentication, null, 1L).block();

        assertEquals(1, controller.listAppRules(authentication, null).collectList().block().size());
        assertNotNull(controller.createAppRule(authentication, null, Mono.just(new SimpleAppPolicyRequest())).block());
        assertNotNull(controller.updateAppRule(authentication, null, 2L, Mono.just(new SimpleAppPolicyRequest())).block());
        controller.deleteAppRule(authentication, null, 2L).block();
        assertEquals("tenant-a", controller.installStarterPack(authentication, null).block().getScope());
        assertEquals(1, controller.listTrustLevels(authentication, null).collectList().block().size());
        assertNotNull(controller.createTrustLevel(authentication, null, Mono.just(new TrustScoreDecisionPolicy())).block());
        assertNotNull(controller.updateTrustLevel(authentication, null, 3L, Mono.just(new TrustScoreDecisionPolicy())).block());
        controller.deleteTrustLevel(authentication, null, 3L).block();
        assertEquals(1, controller.listFixLibrary(authentication, null).collectList().block().size());
        assertEquals(1, controller.listFixOptions(authentication, null).collectList().block().size());
        assertNotNull(controller.createFixLibrary(authentication, null, Mono.just(new RemediationRule())).block());
        assertNotNull(controller.updateFixLibrary(authentication, null, 4L, Mono.just(new RemediationRule())).block());
        controller.deleteFixLibrary(authentication, null, 4L).block();
        assertEquals("QUARANTINE", controller.simulate(authentication, null, Mono.just(new SimplePolicySimulationRequest())).block().getDecisionAction());

        verify(simplePolicyService).listDevicePolicies("TENANT_ADMIN", "tenant-a");
        verify(simplePolicyService).createDevicePolicy(eq("actor"), eq("TENANT_ADMIN"), eq("tenant-a"), any());
        verify(simplePolicyService).updateDevicePolicy(eq("actor"), eq("TENANT_ADMIN"), eq("tenant-a"), eq(1L), any());
        verify(simplePolicyService).deleteDevicePolicy("actor", "TENANT_ADMIN", "tenant-a", 1L);
        verify(simplePolicyService).listAppPolicies("TENANT_ADMIN", "tenant-a");
        verify(simplePolicyService).createAppPolicy(eq("actor"), eq("TENANT_ADMIN"), eq("tenant-a"), any());
        verify(simplePolicyService).updateAppPolicy(eq("actor"), eq("TENANT_ADMIN"), eq("tenant-a"), eq(2L), any());
        verify(simplePolicyService).deleteAppPolicy("actor", "TENANT_ADMIN", "tenant-a", 2L);
        verify(simplePolicyService).installStarterPack("actor", "TENANT_ADMIN", "tenant-a");
        verify(simplePolicyService).listTrustLevels("TENANT_ADMIN", "tenant-a");
        verify(policiesCrudService).createTrustDecisionPolicy(eq("actor"), eq("TENANT_ADMIN"), eq("tenant-a"), any());
        verify(policiesCrudService).updateTrustDecisionPolicy(eq("actor"), eq("TENANT_ADMIN"), eq("tenant-a"), eq(3L), any());
        verify(policiesCrudService).deleteTrustDecisionPolicy("actor", "TENANT_ADMIN", "tenant-a", 3L);
        verify(simplePolicyService).listFixLibrary("TENANT_ADMIN", "tenant-a");
        verify(simplePolicyService).listFixOptions("TENANT_ADMIN", "tenant-a");
        verify(policiesCrudService).createRemediationRule(eq("actor"), eq("TENANT_ADMIN"), eq("tenant-a"), any());
        verify(policiesCrudService).updateRemediationRule(eq("actor"), eq("TENANT_ADMIN"), eq("tenant-a"), eq(4L), any());
        verify(policiesCrudService).deleteRemediationRule("actor", "TENANT_ADMIN", "tenant-a", 4L);
        verify(simplePolicySimulationService).simulate(eq("tenant-a"), any());
    }

    @Test
    void tenantMismatch_bubblesAndBlocksServiceCall() {
        when(requestContext.resolveOptionalTenantId(authentication, "tenant-b"))
                .thenReturn(Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "Tenant scope mismatch")));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> controller.listDeviceChecks(authentication, "tenant-b").collectList().block()
        );

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        verify(simplePolicyService, never()).listDevicePolicies("TENANT_ADMIN", "tenant-b");
    }
}
