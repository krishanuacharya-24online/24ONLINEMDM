package com.e24online.mdm.web;

import com.e24online.mdm.domain.*;
import com.e24online.mdm.records.SystemRuleCloneResult;
import com.e24online.mdm.service.PoliciesCrudService;
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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PoliciesControllerTest {

    @Mock
    private PoliciesCrudService policiesCrudService;

    @Mock
    private AuthenticatedRequestContext requestContext;

    @Mock
    private Authentication authentication;

    private PoliciesController controller;

    @BeforeEach
    void setUp() {
        controller = new PoliciesController(policiesCrudService, requestContext);
        when(requestContext.resolveRole(authentication)).thenReturn("PRODUCT_ADMIN");
        when(requestContext.resolveActor(authentication)).thenReturn("system");
    }

    @Test
    void readEndpoints_delegateToServiceWithScopeContext() {
        when(requestContext.resolveOptionalTenantId(authentication, null)).thenReturn(Mono.just(""));
        when(policiesCrudService.listSystemRules("PRODUCT_ADMIN", "", "ACTIVE", 1, 25))
                .thenReturn(Flux.just(new SystemInformationRule()));
        when(policiesCrudService.getSystemRule("PRODUCT_ADMIN", "", 10L))
                .thenReturn(Mono.just(new SystemInformationRule()));
        when(policiesCrudService.listSystemRuleConditions("PRODUCT_ADMIN", "", 10L))
                .thenReturn(Flux.just(new SystemInformationRuleCondition()));
        when(policiesCrudService.listRejectApps("PRODUCT_ADMIN", "", "WINDOWS", "ACTIVE", 0, 50))
                .thenReturn(Flux.just(new RejectApplication()));
        when(policiesCrudService.getRejectApp("PRODUCT_ADMIN", "", 12L))
                .thenReturn(Mono.just(new RejectApplication()));
        when(policiesCrudService.listTrustScorePolicies("PRODUCT_ADMIN", "", "ACTIVE", 0, 50))
                .thenReturn(Flux.just(new TrustScorePolicy()));
        when(policiesCrudService.getTrustScorePolicy("PRODUCT_ADMIN", "", 13L))
                .thenReturn(Mono.just(new TrustScorePolicy()));
        when(policiesCrudService.listTrustDecisionPolicies("PRODUCT_ADMIN", "", "ACTIVE", 0, 50))
                .thenReturn(Flux.just(new TrustScoreDecisionPolicy()));
        when(policiesCrudService.getTrustDecisionPolicy("PRODUCT_ADMIN", "", 14L))
                .thenReturn(Mono.just(new TrustScoreDecisionPolicy()));
        when(policiesCrudService.listRemediationRules("PRODUCT_ADMIN", "", "ACTIVE", 0, 50))
                .thenReturn(Flux.just(new RemediationRule()));
        when(policiesCrudService.getRemediationRule("PRODUCT_ADMIN", "", 15L))
                .thenReturn(Mono.just(new RemediationRule()));
        when(policiesCrudService.listRuleRemediationMappings("PRODUCT_ADMIN", "", "SYSTEM", 0, 50))
                .thenReturn(Flux.just(new RuleRemediationMapping()));
        when(policiesCrudService.getRuleRemediationMapping("PRODUCT_ADMIN", "", 16L))
                .thenReturn(Mono.just(new RuleRemediationMapping()));
        when(policiesCrudService.cloneSystemRule("system", "PRODUCT_ADMIN", "", 10L))
                .thenReturn(Mono.just(new SystemRuleCloneResult(new SystemInformationRule(), 2)));

        assertNotNull(controller.listSystemRules(authentication, null, "ACTIVE", 1, 25).collectList().block());
        assertNotNull(controller.getSystemRule(authentication, null, 10L).block());
        assertNotNull(controller.listSystemRuleConditions(authentication, null, 10L).collectList().block());
        assertNotNull(controller.cloneSystemRule(authentication, null, 10L).block());
        assertNotNull(controller.listRejectApps(authentication, null, "WINDOWS", "ACTIVE", 0, 50).collectList().block());
        assertNotNull(controller.getRejectApp(authentication, null, 12L).block());
        assertNotNull(controller.listTrustScorePolicies(authentication, null, "ACTIVE", 0, 50).collectList().block());
        assertNotNull(controller.getTrustScorePolicy(authentication, null, 13L).block());
        assertNotNull(controller.listTrustDecisionPolicies(authentication, null, "ACTIVE", 0, 50).collectList().block());
        assertNotNull(controller.getTrustDecisionPolicy(authentication, null, 14L).block());
        assertNotNull(controller.listRemediationRules(authentication, null, "ACTIVE", 0, 50).collectList().block());
        assertNotNull(controller.getRemediationRule(authentication, null, 15L).block());
        assertNotNull(controller.listRuleRemediationMappings(authentication, null, "SYSTEM", 0, 50).collectList().block());
        assertNotNull(controller.getRuleRemediationMapping(authentication, null, 16L).block());

        verify(policiesCrudService).listSystemRules("PRODUCT_ADMIN", "", "ACTIVE", 1, 25);
        verify(policiesCrudService).getSystemRule("PRODUCT_ADMIN", "", 10L);
        verify(policiesCrudService).listSystemRuleConditions("PRODUCT_ADMIN", "", 10L);
        verify(policiesCrudService).cloneSystemRule("system", "PRODUCT_ADMIN", "", 10L);
        verify(policiesCrudService).listRejectApps("PRODUCT_ADMIN", "", "WINDOWS", "ACTIVE", 0, 50);
        verify(policiesCrudService).getRejectApp("PRODUCT_ADMIN", "", 12L);
        verify(policiesCrudService).listTrustScorePolicies("PRODUCT_ADMIN", "", "ACTIVE", 0, 50);
        verify(policiesCrudService).getTrustScorePolicy("PRODUCT_ADMIN", "", 13L);
        verify(policiesCrudService).listTrustDecisionPolicies("PRODUCT_ADMIN", "", "ACTIVE", 0, 50);
        verify(policiesCrudService).getTrustDecisionPolicy("PRODUCT_ADMIN", "", 14L);
        verify(policiesCrudService).listRemediationRules("PRODUCT_ADMIN", "", "ACTIVE", 0, 50);
        verify(policiesCrudService).getRemediationRule("PRODUCT_ADMIN", "", 15L);
        verify(policiesCrudService).listRuleRemediationMappings("PRODUCT_ADMIN", "", "SYSTEM", 0, 50);
        verify(policiesCrudService).getRuleRemediationMapping("PRODUCT_ADMIN", "", 16L);
    }

    @Test
    void writeEndpoints_delegateWithResolvedActorRoleAndTenantScope() {
        when(requestContext.resolveActor(authentication)).thenReturn("actor");
        when(requestContext.resolveRole(authentication)).thenReturn("TENANT_ADMIN");
        when(requestContext.resolveOptionalTenantId(authentication, null)).thenReturn(Mono.just("tenant-a"));

        SystemInformationRule systemRule = new SystemInformationRule();
        SystemInformationRuleCondition condition = new SystemInformationRuleCondition();
        RejectApplication rejectApplication = new RejectApplication();
        TrustScorePolicy scorePolicy = new TrustScorePolicy();
        TrustScoreDecisionPolicy decisionPolicy = new TrustScoreDecisionPolicy();
        RemediationRule remediationRule = new RemediationRule();
        RuleRemediationMapping mapping = new RuleRemediationMapping();
        SystemRuleCloneResult cloneResult =
                new SystemRuleCloneResult(new SystemInformationRule(), 1);

        when(policiesCrudService.createSystemRule(eq("actor"), eq("TENANT_ADMIN"), eq("tenant-a"), any())).thenReturn(Mono.just(systemRule));
        when(policiesCrudService.updateSystemRule(eq("actor"), eq("TENANT_ADMIN"), eq("tenant-a"), eq(1L), any())).thenReturn(Mono.just(systemRule));
        when(policiesCrudService.deleteSystemRule("actor", "TENANT_ADMIN", "tenant-a", 1L)).thenReturn(Mono.empty());
        when(policiesCrudService.cloneSystemRule("actor", "TENANT_ADMIN", "tenant-a", 1L)).thenReturn(Mono.just(cloneResult));
        when(policiesCrudService.createSystemRuleCondition(eq("actor"), eq("TENANT_ADMIN"), eq("tenant-a"), eq(1L), any())).thenReturn(Mono.just(condition));
        when(policiesCrudService.updateSystemRuleCondition(eq("actor"), eq("TENANT_ADMIN"), eq("tenant-a"), eq(1L), eq(2L), any())).thenReturn(Mono.just(condition));
        when(policiesCrudService.deleteSystemRuleCondition("actor", "TENANT_ADMIN", "tenant-a", 1L, 2L)).thenReturn(Mono.empty());

        when(policiesCrudService.createRejectApp(eq("actor"), eq("TENANT_ADMIN"), eq("tenant-a"), any())).thenReturn(Mono.just(rejectApplication));
        when(policiesCrudService.updateRejectApp(eq("actor"), eq("TENANT_ADMIN"), eq("tenant-a"), eq(3L), any())).thenReturn(Mono.just(rejectApplication));
        when(policiesCrudService.deleteRejectApp("actor", "TENANT_ADMIN", "tenant-a", 3L)).thenReturn(Mono.empty());

        when(policiesCrudService.createTrustScorePolicy(eq("actor"), eq("TENANT_ADMIN"), eq("tenant-a"), any())).thenReturn(Mono.just(scorePolicy));
        when(policiesCrudService.updateTrustScorePolicy(eq("actor"), eq("TENANT_ADMIN"), eq("tenant-a"), eq(4L), any())).thenReturn(Mono.just(scorePolicy));
        when(policiesCrudService.deleteTrustScorePolicy("actor", "TENANT_ADMIN", "tenant-a", 4L)).thenReturn(Mono.empty());

        when(policiesCrudService.createTrustDecisionPolicy(eq("actor"), eq("TENANT_ADMIN"), eq("tenant-a"), any())).thenReturn(Mono.just(decisionPolicy));
        when(policiesCrudService.updateTrustDecisionPolicy(eq("actor"), eq("TENANT_ADMIN"), eq("tenant-a"), eq(5L), any())).thenReturn(Mono.just(decisionPolicy));
        when(policiesCrudService.deleteTrustDecisionPolicy("actor", "TENANT_ADMIN", "tenant-a", 5L)).thenReturn(Mono.empty());

        when(policiesCrudService.createRemediationRule(eq("actor"), eq("TENANT_ADMIN"), eq("tenant-a"), any())).thenReturn(Mono.just(remediationRule));
        when(policiesCrudService.updateRemediationRule(eq("actor"), eq("TENANT_ADMIN"), eq("tenant-a"), eq(6L), any())).thenReturn(Mono.just(remediationRule));
        when(policiesCrudService.deleteRemediationRule("actor", "TENANT_ADMIN", "tenant-a", 6L)).thenReturn(Mono.empty());

        when(policiesCrudService.createRuleRemediationMapping(eq("actor"), eq("TENANT_ADMIN"), eq("tenant-a"), any())).thenReturn(Mono.just(mapping));
        when(policiesCrudService.updateRuleRemediationMapping(eq("actor"), eq("TENANT_ADMIN"), eq("tenant-a"), eq(7L), any())).thenReturn(Mono.just(mapping));
        when(policiesCrudService.deleteRuleRemediationMapping("actor", "TENANT_ADMIN", "tenant-a", 7L)).thenReturn(Mono.empty());

        assertNotNull(controller.createSystemRule(authentication, null, Mono.just(systemRule)).block());
        assertNotNull(controller.updateSystemRule(authentication, null, 1L, Mono.just(systemRule)).block());
        controller.deleteSystemRule(authentication, null, 1L).block();
        assertNotNull(controller.cloneSystemRule(authentication, null, 1L).block());
        assertNotNull(controller.createSystemRuleCondition(authentication, null, 1L, Mono.just(condition)).block());
        assertNotNull(controller.updateSystemRuleCondition(authentication, null, 1L, 2L, Mono.just(condition)).block());
        controller.deleteSystemRuleCondition(authentication, null, 1L, 2L).block();

        assertNotNull(controller.createRejectApp(authentication, null, Mono.just(rejectApplication)).block());
        assertNotNull(controller.updateRejectApp(authentication, null, 3L, Mono.just(rejectApplication)).block());
        controller.deleteRejectApp(authentication, null, 3L).block();

        assertNotNull(controller.createTrustScorePolicy(authentication, null, Mono.just(scorePolicy)).block());
        assertNotNull(controller.updateTrustScorePolicy(authentication, null, 4L, Mono.just(scorePolicy)).block());
        controller.deleteTrustScorePolicy(authentication, null, 4L).block();

        assertNotNull(controller.createTrustDecisionPolicy(authentication, null, Mono.just(decisionPolicy)).block());
        assertNotNull(controller.updateTrustDecisionPolicy(authentication, null, 5L, Mono.just(decisionPolicy)).block());
        controller.deleteTrustDecisionPolicy(authentication, null, 5L).block();

        assertNotNull(controller.createRemediationRule(authentication, null, Mono.just(remediationRule)).block());
        assertNotNull(controller.updateRemediationRule(authentication, null, 6L, Mono.just(remediationRule)).block());
        controller.deleteRemediationRule(authentication, null, 6L).block();

        assertNotNull(controller.createRuleRemediationMapping(authentication, null, Mono.just(mapping)).block());
        assertNotNull(controller.updateRuleRemediationMapping(authentication, null, 7L, Mono.just(mapping)).block());
        controller.deleteRuleRemediationMapping(authentication, null, 7L).block();

        verify(requestContext, atLeastOnce()).resolveActor(authentication);
        verify(requestContext, atLeastOnce()).resolveRole(authentication);
        verify(requestContext, atLeastOnce()).resolveOptionalTenantId(authentication, null);
        verify(policiesCrudService).createSystemRule(eq("actor"), eq("TENANT_ADMIN"), eq("tenant-a"), any());
        verify(policiesCrudService).updateSystemRule(eq("actor"), eq("TENANT_ADMIN"), eq("tenant-a"), eq(1L), any());
        verify(policiesCrudService).deleteSystemRule("actor", "TENANT_ADMIN", "tenant-a", 1L);
        verify(policiesCrudService).cloneSystemRule("actor", "TENANT_ADMIN", "tenant-a", 1L);
        verify(policiesCrudService).createSystemRuleCondition(eq("actor"), eq("TENANT_ADMIN"), eq("tenant-a"), eq(1L), any());
        verify(policiesCrudService).updateSystemRuleCondition(eq("actor"), eq("TENANT_ADMIN"), eq("tenant-a"), eq(1L), eq(2L), any());
        verify(policiesCrudService).deleteSystemRuleCondition("actor", "TENANT_ADMIN", "tenant-a", 1L, 2L);
        verify(policiesCrudService).createRejectApp(eq("actor"), eq("TENANT_ADMIN"), eq("tenant-a"), any());
        verify(policiesCrudService).updateRejectApp(eq("actor"), eq("TENANT_ADMIN"), eq("tenant-a"), eq(3L), any());
        verify(policiesCrudService).deleteRejectApp("actor", "TENANT_ADMIN", "tenant-a", 3L);
        verify(policiesCrudService).createTrustScorePolicy(eq("actor"), eq("TENANT_ADMIN"), eq("tenant-a"), any());
        verify(policiesCrudService).updateTrustScorePolicy(eq("actor"), eq("TENANT_ADMIN"), eq("tenant-a"), eq(4L), any());
        verify(policiesCrudService).deleteTrustScorePolicy("actor", "TENANT_ADMIN", "tenant-a", 4L);
        verify(policiesCrudService).createTrustDecisionPolicy(eq("actor"), eq("TENANT_ADMIN"), eq("tenant-a"), any());
        verify(policiesCrudService).updateTrustDecisionPolicy(eq("actor"), eq("TENANT_ADMIN"), eq("tenant-a"), eq(5L), any());
        verify(policiesCrudService).deleteTrustDecisionPolicy("actor", "TENANT_ADMIN", "tenant-a", 5L);
        verify(policiesCrudService).createRemediationRule(eq("actor"), eq("TENANT_ADMIN"), eq("tenant-a"), any());
        verify(policiesCrudService).updateRemediationRule(eq("actor"), eq("TENANT_ADMIN"), eq("tenant-a"), eq(6L), any());
        verify(policiesCrudService).deleteRemediationRule("actor", "TENANT_ADMIN", "tenant-a", 6L);
        verify(policiesCrudService).createRuleRemediationMapping(eq("actor"), eq("TENANT_ADMIN"), eq("tenant-a"), any());
        verify(policiesCrudService).updateRuleRemediationMapping(eq("actor"), eq("TENANT_ADMIN"), eq("tenant-a"), eq(7L), any());
        verify(policiesCrudService).deleteRuleRemediationMapping("actor", "TENANT_ADMIN", "tenant-a", 7L);
    }

    @Test
    void tenantScopeMismatch_bubblesAsForbidden() {
        when(requestContext.resolveOptionalTenantId(authentication, "tenant-b"))
                .thenReturn(Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "Tenant scope mismatch")));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> controller.listSystemRules(authentication, "tenant-b", "ACTIVE", 0, 10).collectList().block()
        );

        assertNotNull(ex);
        verify(policiesCrudService, never()).listSystemRules(anyString(), anyString(), anyString(), anyInt(), anyInt());
    }

    @Test
    void forbiddenServiceWrite_bubblesForTenantAdminOnGlobalRecord() {
        when(requestContext.resolveActor(authentication)).thenReturn("actor");
        when(requestContext.resolveRole(authentication)).thenReturn("TENANT_ADMIN");
        when(requestContext.resolveOptionalTenantId(authentication, null)).thenReturn(Mono.just("tenant-a"));
        when(policiesCrudService.updateSystemRule(eq("actor"), eq("TENANT_ADMIN"), eq("tenant-a"), eq(99L), any()))
                .thenReturn(Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot modify this system rule")));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> controller.updateSystemRule(authentication, null, 99L, Mono.just(new SystemInformationRule())).block()
        );

        assertNotNull(ex);
        verify(policiesCrudService).updateSystemRule(eq("actor"), eq("TENANT_ADMIN"), eq("tenant-a"), eq(99L), any());
    }
}
