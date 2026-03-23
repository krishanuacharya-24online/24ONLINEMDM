package com.e24online.mdm.web;

import com.e24online.mdm.domain.RemediationRule;
import com.e24online.mdm.domain.TrustScoreDecisionPolicy;
import com.e24online.mdm.records.PolicyContext;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("${api.version.prefix:v1}/policies/simple")
@PreAuthorize("hasAnyRole('PRODUCT_ADMIN','TENANT_ADMIN')")
public class SimplePoliciesController {

    private final PoliciesCrudService policiesCrudService;
    private final SimplePolicyService simplePolicyService;
    private final SimplePolicySimulationService simplePolicySimulationService;
    private final AuthenticatedRequestContext requestContext;

    public SimplePoliciesController(PoliciesCrudService policiesCrudService,
                                    SimplePolicyService simplePolicyService,
                                    SimplePolicySimulationService simplePolicySimulationService,
                                    AuthenticatedRequestContext requestContext) {
        this.policiesCrudService = policiesCrudService;
        this.simplePolicyService = simplePolicyService;
        this.simplePolicySimulationService = simplePolicySimulationService;
        this.requestContext = requestContext;
    }

    @GetMapping("/device-checks")
    public Flux<SimpleDevicePolicySummary> listDeviceChecks(Authentication authentication,
                                                            @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId) {
        return resolvePolicyContext(authentication, tenantId)
                .flatMapMany(ctx -> simplePolicyService.listDevicePolicies(ctx.role(), ctx.tenantId()));
    }

    @PostMapping("/device-checks")
    public Mono<SimpleDevicePolicySummary> createDeviceCheck(Authentication authentication,
                                                             @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId,
                                                             @RequestBody Mono<SimpleDevicePolicyRequest> request) {
        return resolvePolicyContext(authentication, tenantId)
                .flatMap(ctx -> simplePolicyService.createDevicePolicy(ctx.actor(), ctx.role(), ctx.tenantId(), request));
    }

    @PutMapping("/device-checks/{id}")
    public Mono<SimpleDevicePolicySummary> updateDeviceCheck(Authentication authentication,
                                                             @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId,
                                                             @PathVariable("id") Long id,
                                                             @RequestBody Mono<SimpleDevicePolicyRequest> request) {
        return resolvePolicyContext(authentication, tenantId)
                .flatMap(ctx -> simplePolicyService.updateDevicePolicy(ctx.actor(), ctx.role(), ctx.tenantId(), id, request));
    }

    @DeleteMapping("/device-checks/{id}")
    public Mono<Void> deleteDeviceCheck(Authentication authentication,
                                        @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId,
                                        @PathVariable("id") Long id) {
        return resolvePolicyContext(authentication, tenantId)
                .flatMap(ctx -> simplePolicyService.deleteDevicePolicy(ctx.actor(), ctx.role(), ctx.tenantId(), id));
    }

    @GetMapping("/app-rules")
    public Flux<SimpleAppPolicySummary> listAppRules(Authentication authentication,
                                                     @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId) {
        return resolvePolicyContext(authentication, tenantId)
                .flatMapMany(ctx -> simplePolicyService.listAppPolicies(ctx.role(), ctx.tenantId()));
    }

    @PostMapping("/app-rules")
    public Mono<SimpleAppPolicySummary> createAppRule(Authentication authentication,
                                                      @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId,
                                                      @RequestBody Mono<SimpleAppPolicyRequest> request) {
        return resolvePolicyContext(authentication, tenantId)
                .flatMap(ctx -> simplePolicyService.createAppPolicy(ctx.actor(), ctx.role(), ctx.tenantId(), request));
    }

    @PutMapping("/app-rules/{id}")
    public Mono<SimpleAppPolicySummary> updateAppRule(Authentication authentication,
                                                      @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId,
                                                      @PathVariable("id") Long id,
                                                      @RequestBody Mono<SimpleAppPolicyRequest> request) {
        return resolvePolicyContext(authentication, tenantId)
                .flatMap(ctx -> simplePolicyService.updateAppPolicy(ctx.actor(), ctx.role(), ctx.tenantId(), id, request));
    }

    @DeleteMapping("/app-rules/{id}")
    public Mono<Void> deleteAppRule(Authentication authentication,
                                    @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId,
                                    @PathVariable("id") Long id) {
        return resolvePolicyContext(authentication, tenantId)
                .flatMap(ctx -> simplePolicyService.deleteAppPolicy(ctx.actor(), ctx.role(), ctx.tenantId(), id));
    }

    @PostMapping("/starter-pack")
    public Mono<SimplePolicyStarterPackSummary> installStarterPack(Authentication authentication,
                                                                   @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId) {
        return resolvePolicyContext(authentication, tenantId)
                .flatMap(ctx -> simplePolicyService.installStarterPack(ctx.actor(), ctx.role(), ctx.tenantId()));
    }

    @GetMapping("/trust-levels")
    public Flux<TrustScoreDecisionPolicy> listTrustLevels(Authentication authentication,
                                                          @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId) {
        return resolvePolicyContext(authentication, tenantId)
                .flatMapMany(ctx -> simplePolicyService.listTrustLevels(ctx.role(), ctx.tenantId()));
    }

    @PostMapping("/trust-levels")
    public Mono<TrustScoreDecisionPolicy> createTrustLevel(Authentication authentication,
                                                           @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId,
                                                           @RequestBody Mono<TrustScoreDecisionPolicy> request) {
        return resolvePolicyContext(authentication, tenantId)
                .flatMap(ctx -> policiesCrudService.createTrustDecisionPolicy(ctx.actor(), ctx.role(), ctx.tenantId(), request));
    }

    @PutMapping("/trust-levels/{id}")
    public Mono<TrustScoreDecisionPolicy> updateTrustLevel(Authentication authentication,
                                                           @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId,
                                                           @PathVariable("id") Long id,
                                                           @RequestBody Mono<TrustScoreDecisionPolicy> request) {
        return resolvePolicyContext(authentication, tenantId)
                .flatMap(ctx -> policiesCrudService.updateTrustDecisionPolicy(ctx.actor(), ctx.role(), ctx.tenantId(), id, request));
    }

    @DeleteMapping("/trust-levels/{id}")
    public Mono<Void> deleteTrustLevel(Authentication authentication,
                                       @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId,
                                       @PathVariable("id") Long id) {
        return resolvePolicyContext(authentication, tenantId)
                .flatMap(ctx -> policiesCrudService.deleteTrustDecisionPolicy(ctx.actor(), ctx.role(), ctx.tenantId(), id));
    }

    @GetMapping("/fix-library")
    public Flux<RemediationRule> listFixLibrary(Authentication authentication,
                                                @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId) {
        return resolvePolicyContext(authentication, tenantId)
                .flatMapMany(ctx -> simplePolicyService.listFixLibrary(ctx.role(), ctx.tenantId()));
    }

    @GetMapping("/fix-options")
    public Flux<RemediationRule> listFixOptions(Authentication authentication,
                                                @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId) {
        return resolvePolicyContext(authentication, tenantId)
                .flatMapMany(ctx -> simplePolicyService.listFixOptions(ctx.role(), ctx.tenantId()));
    }

    @PostMapping("/fix-library")
    public Mono<RemediationRule> createFixLibrary(Authentication authentication,
                                                  @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId,
                                                  @RequestBody Mono<RemediationRule> request) {
        return resolvePolicyContext(authentication, tenantId)
                .flatMap(ctx -> policiesCrudService.createRemediationRule(ctx.actor(), ctx.role(), ctx.tenantId(), request));
    }

    @PutMapping("/fix-library/{id}")
    public Mono<RemediationRule> updateFixLibrary(Authentication authentication,
                                                  @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId,
                                                  @PathVariable("id") Long id,
                                                  @RequestBody Mono<RemediationRule> request) {
        return resolvePolicyContext(authentication, tenantId)
                .flatMap(ctx -> policiesCrudService.updateRemediationRule(ctx.actor(), ctx.role(), ctx.tenantId(), id, request));
    }

    @DeleteMapping("/fix-library/{id}")
    public Mono<Void> deleteFixLibrary(Authentication authentication,
                                       @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId,
                                       @PathVariable("id") Long id) {
        return resolvePolicyContext(authentication, tenantId)
                .flatMap(ctx -> policiesCrudService.deleteRemediationRule(ctx.actor(), ctx.role(), ctx.tenantId(), id));
    }

    @PostMapping("/simulate")
    public Mono<SimplePolicySimulationResponse> simulate(Authentication authentication,
                                                         @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId,
                                                         @RequestBody Mono<SimplePolicySimulationRequest> request) {
        return resolvePolicyContext(authentication, tenantId)
                .flatMap(ctx -> simplePolicySimulationService.simulate(ctx.tenantId(), request));
    }

    private Mono<PolicyContext> resolvePolicyContext(Authentication authentication, String requestedTenantId) {
        String actor = requestContext.resolveActor(authentication);
        String role = requestContext.resolveRole(authentication);
        return requestContext.resolveOptionalTenantId(authentication, requestedTenantId)
                .map(tenantId -> new PolicyContext(actor, role, normalizeOptionalTenantId(tenantId)))
                .defaultIfEmpty(new PolicyContext(actor, role, null));
    }

    private String normalizeOptionalTenantId(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            return null;
        }
        return tenantId.trim().toLowerCase();
    }
}
