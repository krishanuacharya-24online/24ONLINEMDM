package com.e24online.mdm.web;

import com.e24online.mdm.domain.*;
import com.e24online.mdm.service.PoliciesCrudService;
import com.e24online.mdm.web.security.AuthenticatedRequestContext;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("${api.version.prefix:v1}/policies")
@PreAuthorize("hasAnyRole('PRODUCT_ADMIN','TENANT_ADMIN')")
public class PoliciesController {

    private final PoliciesCrudService policiesCrudService;
    private final AuthenticatedRequestContext requestContext;

    public PoliciesController(PoliciesCrudService policiesCrudService,
                              AuthenticatedRequestContext requestContext) {
        this.policiesCrudService = policiesCrudService;
        this.requestContext = requestContext;
    }

    @GetMapping("/system-rules")
    public Flux<SystemInformationRule> listSystemRules(
            Authentication authentication,
            @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "50") int size
    ) {
        return resolvePolicyContext(authentication, tenantId)
                .flatMapMany(ctx -> policiesCrudService.listSystemRules(ctx.role(), ctx.tenantId(), status, page, size));
    }

    @GetMapping("/system-rules/{id}")
    public Mono<SystemInformationRule> getSystemRule(
            Authentication authentication,
            @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId,
            @PathVariable("id") Long id
    ) {
        return resolvePolicyContext(authentication, tenantId)
                .flatMap(ctx -> policiesCrudService.getSystemRule(ctx.role(), ctx.tenantId(), id));
    }

    @GetMapping("/system-rules/{id}/conditions")
    public Flux<SystemInformationRuleCondition> listSystemRuleConditions(
            Authentication authentication,
            @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId,
            @PathVariable("id") Long id
    ) {
        return resolvePolicyContext(authentication, tenantId)
                .flatMapMany(ctx -> policiesCrudService.listSystemRuleConditions(ctx.role(), ctx.tenantId(), id));
    }

    @PostMapping("/system-rules")
    public Mono<SystemInformationRule> createSystemRule(
            Authentication authentication,
            @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId,
            @Valid @RequestBody Mono<SystemInformationRule> request
    ) {
        return resolvePolicyContext(authentication, tenantId)
                .flatMap(ctx -> policiesCrudService.createSystemRule(ctx.actor(), ctx.role(), ctx.tenantId(), request));
    }

    @PutMapping("/system-rules/{id}")
    public Mono<SystemInformationRule> updateSystemRule(
            Authentication authentication,
            @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId,
            @PathVariable("id") Long id,
            @Valid @RequestBody Mono<SystemInformationRule> request
    ) {
        return resolvePolicyContext(authentication, tenantId)
                .flatMap(ctx -> policiesCrudService.updateSystemRule(ctx.actor(), ctx.role(), ctx.tenantId(), id, request));
    }

    @DeleteMapping("/system-rules/{id}")
    public Mono<Void> deleteSystemRule(
            Authentication authentication,
            @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId,
            @PathVariable("id") Long id
    ) {
        return resolvePolicyContext(authentication, tenantId)
                .flatMap(ctx -> policiesCrudService.deleteSystemRule(ctx.actor(), ctx.role(), ctx.tenantId(), id));
    }

    @PostMapping("/system-rules/{id}/clone")
    public Mono<PoliciesCrudService.SystemRuleCloneResult> cloneSystemRule(
            Authentication authentication,
            @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId,
            @PathVariable("id") Long id
    ) {
        return resolvePolicyContext(authentication, tenantId)
                .flatMap(ctx -> policiesCrudService.cloneSystemRule(ctx.actor(), ctx.role(), ctx.tenantId(), id));
    }

    @PostMapping("/system-rules/{id}/conditions")
    public Mono<SystemInformationRuleCondition> createSystemRuleCondition(
            Authentication authentication,
            @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId,
            @PathVariable("id") Long ruleId,
            @Valid @RequestBody Mono<SystemInformationRuleCondition> request
    ) {
        return resolvePolicyContext(authentication, tenantId)
                .flatMap(ctx -> policiesCrudService.createSystemRuleCondition(ctx.actor(), ctx.role(), ctx.tenantId(), ruleId, request));
    }

    @PutMapping("/system-rules/{id}/conditions/{condition_id}")
    public Mono<SystemInformationRuleCondition> updateSystemRuleCondition(
            Authentication authentication,
            @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId,
            @PathVariable("id") Long ruleId,
            @PathVariable("condition_id") Long conditionId,
            @Valid @RequestBody Mono<SystemInformationRuleCondition> request
    ) {
        return resolvePolicyContext(authentication, tenantId)
                .flatMap(ctx -> policiesCrudService.updateSystemRuleCondition(ctx.actor(), ctx.role(), ctx.tenantId(), ruleId, conditionId, request));
    }

    @DeleteMapping("/system-rules/{id}/conditions/{condition_id}")
    public Mono<Void> deleteSystemRuleCondition(
            Authentication authentication,
            @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId,
            @PathVariable("id") Long ruleId,
            @PathVariable("condition_id") Long conditionId
    ) {
        return resolvePolicyContext(authentication, tenantId)
                .flatMap(ctx -> policiesCrudService.deleteSystemRuleCondition(ctx.actor(), ctx.role(), ctx.tenantId(), ruleId, conditionId));
    }

    @GetMapping("/reject-apps")
    public Flux<RejectApplication> listRejectApps(
            Authentication authentication,
            @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId,
            @RequestParam(name = "app_os_type", required = false) String osType,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "50") int size
    ) {
        return resolvePolicyContext(authentication, tenantId)
                .flatMapMany(ctx -> policiesCrudService.listRejectApps(ctx.role(), ctx.tenantId(), osType, status, page, size));
    }

    @GetMapping("/reject-apps/{id}")
    public Mono<RejectApplication> getRejectApp(
            Authentication authentication,
            @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId,
            @PathVariable("id") Long id
    ) {
        return resolvePolicyContext(authentication, tenantId)
                .flatMap(ctx -> policiesCrudService.getRejectApp(ctx.role(), ctx.tenantId(), id));
    }

    @PostMapping("/reject-apps")
    public Mono<RejectApplication> createRejectApp(
            Authentication authentication,
            @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId,
            @Valid @RequestBody Mono<RejectApplication> request
    ) {
        return resolvePolicyContext(authentication, tenantId)
                .flatMap(ctx -> policiesCrudService.createRejectApp(ctx.actor(), ctx.role(), ctx.tenantId(), request));
    }

    @PutMapping("/reject-apps/{id}")
    public Mono<RejectApplication> updateRejectApp(
            Authentication authentication,
            @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId,
            @PathVariable("id") Long id,
            @Valid @RequestBody Mono<RejectApplication> request
    ) {
        return resolvePolicyContext(authentication, tenantId)
                .flatMap(ctx -> policiesCrudService.updateRejectApp(ctx.actor(), ctx.role(), ctx.tenantId(), id, request));
    }

    @DeleteMapping("/reject-apps/{id}")
    public Mono<Void> deleteRejectApp(
            Authentication authentication,
            @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId,
            @PathVariable("id") Long id
    ) {
        return resolvePolicyContext(authentication, tenantId)
                .flatMap(ctx -> policiesCrudService.deleteRejectApp(ctx.actor(), ctx.role(), ctx.tenantId(), id));
    }

    @GetMapping("/trust-score-policies")
    public Flux<TrustScorePolicy> listTrustScorePolicies(
            Authentication authentication,
            @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "50") int size
    ) {
        return resolvePolicyContext(authentication, tenantId)
                .flatMapMany(ctx -> policiesCrudService.listTrustScorePolicies(ctx.role(), ctx.tenantId(), status, page, size));
    }

    @GetMapping("/trust-score-policies/{id}")
    public Mono<TrustScorePolicy> getTrustScorePolicy(
            Authentication authentication,
            @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId,
            @PathVariable("id") Long id
    ) {
        return resolvePolicyContext(authentication, tenantId)
                .flatMap(ctx -> policiesCrudService.getTrustScorePolicy(ctx.role(), ctx.tenantId(), id));
    }

    @PostMapping("/trust-score-policies")
    public Mono<TrustScorePolicy> createTrustScorePolicy(
            Authentication authentication,
            @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId,
            @Valid @RequestBody Mono<TrustScorePolicy> request
    ) {
        return resolvePolicyContext(authentication, tenantId)
                .flatMap(ctx -> policiesCrudService.createTrustScorePolicy(ctx.actor(), ctx.role(), ctx.tenantId(), request));
    }

    @PutMapping("/trust-score-policies/{id}")
    public Mono<TrustScorePolicy> updateTrustScorePolicy(
            Authentication authentication,
            @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId,
            @PathVariable("id") Long id,
            @Valid @RequestBody Mono<TrustScorePolicy> request
    ) {
        return resolvePolicyContext(authentication, tenantId)
                .flatMap(ctx -> policiesCrudService.updateTrustScorePolicy(ctx.actor(), ctx.role(), ctx.tenantId(), id, request));
    }

    @DeleteMapping("/trust-score-policies/{id}")
    public Mono<Void> deleteTrustScorePolicy(
            Authentication authentication,
            @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId,
            @PathVariable("id") Long id
    ) {
        return resolvePolicyContext(authentication, tenantId)
                .flatMap(ctx -> policiesCrudService.deleteTrustScorePolicy(ctx.actor(), ctx.role(), ctx.tenantId(), id));
    }

    @GetMapping("/trust-decision-policies")
    public Flux<TrustScoreDecisionPolicy> listTrustDecisionPolicies(
            Authentication authentication,
            @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "50") int size
    ) {
        return resolvePolicyContext(authentication, tenantId)
                .flatMapMany(ctx -> policiesCrudService.listTrustDecisionPolicies(ctx.role(), ctx.tenantId(), status, page, size));
    }

    @GetMapping("/trust-decision-policies/{id}")
    public Mono<TrustScoreDecisionPolicy> getTrustDecisionPolicy(
            Authentication authentication,
            @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId,
            @PathVariable("id") Long id
    ) {
        return resolvePolicyContext(authentication, tenantId)
                .flatMap(ctx -> policiesCrudService.getTrustDecisionPolicy(ctx.role(), ctx.tenantId(), id));
    }

    @PostMapping("/trust-decision-policies")
    public Mono<TrustScoreDecisionPolicy> createTrustDecisionPolicy(
            Authentication authentication,
            @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId,
            @Valid @RequestBody Mono<TrustScoreDecisionPolicy> request
    ) {
        return resolvePolicyContext(authentication, tenantId)
                .flatMap(ctx -> policiesCrudService.createTrustDecisionPolicy(ctx.actor(), ctx.role(), ctx.tenantId(), request));
    }

    @PutMapping("/trust-decision-policies/{id}")
    public Mono<TrustScoreDecisionPolicy> updateTrustDecisionPolicy(
            Authentication authentication,
            @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId,
            @PathVariable("id") Long id,
            @Valid @RequestBody Mono<TrustScoreDecisionPolicy> request
    ) {
        return resolvePolicyContext(authentication, tenantId)
                .flatMap(ctx -> policiesCrudService.updateTrustDecisionPolicy(ctx.actor(), ctx.role(), ctx.tenantId(), id, request));
    }

    @DeleteMapping("/trust-decision-policies/{id}")
    public Mono<Void> deleteTrustDecisionPolicy(
            Authentication authentication,
            @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId,
            @PathVariable("id") Long id
    ) {
        return resolvePolicyContext(authentication, tenantId)
                .flatMap(ctx -> policiesCrudService.deleteTrustDecisionPolicy(ctx.actor(), ctx.role(), ctx.tenantId(), id));
    }

    @GetMapping("/remediation-rules")
    public Flux<RemediationRule> listRemediationRules(
            Authentication authentication,
            @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "50") int size
    ) {
        return resolvePolicyContext(authentication, tenantId)
                .flatMapMany(ctx -> policiesCrudService.listRemediationRules(ctx.role(), ctx.tenantId(), status, page, size));
    }

    @GetMapping("/remediation-rules/{id}")
    public Mono<RemediationRule> getRemediationRule(
            Authentication authentication,
            @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId,
            @PathVariable("id") Long id
    ) {
        return resolvePolicyContext(authentication, tenantId)
                .flatMap(ctx -> policiesCrudService.getRemediationRule(ctx.role(), ctx.tenantId(), id));
    }

    @PostMapping("/remediation-rules")
    public Mono<RemediationRule> createRemediationRule(
            Authentication authentication,
            @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId,
            @Valid @RequestBody Mono<RemediationRule> request
    ) {
        return resolvePolicyContext(authentication, tenantId)
                .flatMap(ctx -> policiesCrudService.createRemediationRule(ctx.actor(), ctx.role(), ctx.tenantId(), request));
    }

    @PutMapping("/remediation-rules/{id}")
    public Mono<RemediationRule> updateRemediationRule(
            Authentication authentication,
            @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId,
            @PathVariable("id") Long id,
            @Valid @RequestBody Mono<RemediationRule> request
    ) {
        return resolvePolicyContext(authentication, tenantId)
                .flatMap(ctx -> policiesCrudService.updateRemediationRule(ctx.actor(), ctx.role(), ctx.tenantId(), id, request));
    }

    @DeleteMapping("/remediation-rules/{id}")
    public Mono<Void> deleteRemediationRule(
            Authentication authentication,
            @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId,
            @PathVariable("id") Long id
    ) {
        return resolvePolicyContext(authentication, tenantId)
                .flatMap(ctx -> policiesCrudService.deleteRemediationRule(ctx.actor(), ctx.role(), ctx.tenantId(), id));
    }

    @GetMapping("/rule-remediation-mappings")
    public Flux<RuleRemediationMapping> listRuleRemediationMappings(
            Authentication authentication,
            @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId,
            @RequestParam(name = "source_type", required = false) String sourceType,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "50") int size
    ) {
        return resolvePolicyContext(authentication, tenantId)
                .flatMapMany(ctx -> policiesCrudService.listRuleRemediationMappings(ctx.role(), ctx.tenantId(), sourceType, page, size));
    }

    @GetMapping("/rule-remediation-mappings/{id}")
    public Mono<RuleRemediationMapping> getRuleRemediationMapping(
            Authentication authentication,
            @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId,
            @PathVariable("id") Long id
    ) {
        return resolvePolicyContext(authentication, tenantId)
                .flatMap(ctx -> policiesCrudService.getRuleRemediationMapping(ctx.role(), ctx.tenantId(), id));
    }

    @PostMapping("/rule-remediation-mappings")
    public Mono<RuleRemediationMapping> createRuleRemediationMapping(
            Authentication authentication,
            @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId,
            @Valid @RequestBody Mono<RuleRemediationMapping> request
    ) {
        return resolvePolicyContext(authentication, tenantId)
                .flatMap(ctx -> policiesCrudService.createRuleRemediationMapping(ctx.actor(), ctx.role(), ctx.tenantId(), request));
    }

    @PutMapping("/rule-remediation-mappings/{id}")
    public Mono<RuleRemediationMapping> updateRuleRemediationMapping(
            Authentication authentication,
            @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId,
            @PathVariable("id") Long id,
            @Valid @RequestBody Mono<RuleRemediationMapping> request
    ) {
        return resolvePolicyContext(authentication, tenantId)
                .flatMap(ctx -> policiesCrudService.updateRuleRemediationMapping(ctx.actor(), ctx.role(), ctx.tenantId(), id, request));
    }

    @DeleteMapping("/rule-remediation-mappings/{id}")
    public Mono<Void> deleteRuleRemediationMapping(
            Authentication authentication,
            @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId,
            @PathVariable("id") Long id
    ) {
        return resolvePolicyContext(authentication, tenantId)
                .flatMap(ctx -> policiesCrudService.deleteRuleRemediationMapping(ctx.actor(), ctx.role(), ctx.tenantId(), id));
    }

    private Mono<PolicyContext> resolvePolicyContext(Authentication authentication, String requestedTenantId) {
        String actor = requestContext.resolveActor(authentication);
        String role = requestContext.resolveRole(authentication);
        return requestContext.resolveOptionalTenantId(authentication, requestedTenantId)
                .map(tenantId -> new PolicyContext(actor, role, tenantId));
    }

    private record PolicyContext(String actor, String role, String tenantId) {
    }
}
