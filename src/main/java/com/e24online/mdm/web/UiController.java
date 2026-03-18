package com.e24online.mdm.web;

import com.e24online.mdm.repository.DeviceTrustProfileRepository;
import com.e24online.mdm.repository.PostureEvaluationRemediationRepository;
import com.e24online.mdm.service.BlockingDb;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import reactor.core.publisher.Mono;

@Controller
@RequestMapping("/ui")
public class UiController {

    private final DeviceTrustProfileRepository profileRepository;
    private final PostureEvaluationRemediationRepository remediationRepository;
    private final BlockingDb blockingDb;

    public UiController(DeviceTrustProfileRepository profileRepository,
                        PostureEvaluationRemediationRepository remediationRepository,
                        BlockingDb blockingDb) {
        this.profileRepository = profileRepository;
        this.remediationRepository = remediationRepository;
        this.blockingDb = blockingDb;
    }

    @GetMapping({"", "/"})
    @PreAuthorize("hasAnyRole('PRODUCT_ADMIN','TENANT_ADMIN')")
    public Mono<String> overview(Model model) {
        model.addAttribute("activePage", "overview");
        return blockingDb.mono(() -> {
                    long total = profileRepository.countActive();
                    long trusted = profileRepository.countTrusted();
                    long highRisk = profileRepository.countHighRisk();
                    long openRemediation = remediationRepository.countOpenRemediations();
                    model.addAttribute("totalDevices", total);
                    model.addAttribute("trustedDevices", trusted);
                    model.addAttribute("highRiskDevices", highRisk);
                    model.addAttribute("healthyDevices", trusted);
                    model.addAttribute("openRemediations", openRemediation);
                    model.addAttribute("title", "MDM Overview");
                    return "overview";
                });
    }

    @GetMapping("/devices")
    @PreAuthorize("hasAnyRole('PRODUCT_ADMIN','TENANT_ADMIN','TENANT_USER')")
    public Mono<String> devices(Model model) {
        model.addAttribute("activePage", "devices");
        model.addAttribute("title", "Devices");
        return Mono.just("devices");
    }

    @GetMapping("/enrollments")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','TENANT_USER')")
    public Mono<String> enrollments(Model model) {
        model.addAttribute("activePage", "enrollments");
        model.addAttribute("title", "Enrollments");
        return Mono.just("enrollments");
    }

    @GetMapping("/payloads")
    @PreAuthorize("hasRole('PRODUCT_ADMIN')")
    public Mono<String> payloads(Model model) {
        model.addAttribute("activePage", "payloads");
        model.addAttribute("title", "Payloads");
        return Mono.just("payloads");
    }

    @GetMapping("/audit-trail")
    @PreAuthorize("hasAnyRole('PRODUCT_ADMIN','TENANT_ADMIN','AUDITOR')")
    public Mono<String> auditTrail(Model model) {
        model.addAttribute("activePage", "audit");
        model.addAttribute("title", "Audit trail");
        return Mono.just("audit_trail");
    }

    @GetMapping("/policies/system-rules")
    @PreAuthorize("hasAnyRole('PRODUCT_ADMIN','TENANT_ADMIN')")
    public Mono<String> policiesSystemRules(Model model) {
        model.addAttribute("activePage", "policies");
        model.addAttribute("activePolicy", "system-rules");
        model.addAttribute("title", "System rules");
        return Mono.just("policies_system_rules");
    }

    @GetMapping("/policies/system-rules/{id}/conditions")
    @PreAuthorize("hasAnyRole('PRODUCT_ADMIN','TENANT_ADMIN')")
    public Mono<String> policiesSystemRuleConditions(
            @PathVariable("id") Long id,
            Model model
    ) {
        model.addAttribute("activePage", "policies");
        model.addAttribute("activePolicy", "system-rules");
        model.addAttribute("title", "System rule conditions");
        model.addAttribute("ruleId", id);
        return Mono.just("policies_system_rule_conditions");
    }

    @GetMapping("/policies/reject-apps")
    @PreAuthorize("hasAnyRole('PRODUCT_ADMIN','TENANT_ADMIN')")
    public Mono<String> policiesRejectApps(Model model) {
        model.addAttribute("activePage", "policies");
        model.addAttribute("activePolicy", "reject-apps");
        model.addAttribute("title", "Reject applications");
        return Mono.just("policies_reject_apps");
    }

    @GetMapping("/policies/trust-score-policies")
    @PreAuthorize("hasAnyRole('PRODUCT_ADMIN','TENANT_ADMIN')")
    public Mono<String> policiesTrustScorePolicies(Model model) {
        model.addAttribute("activePage", "policies");
        model.addAttribute("activePolicy", "trust-score-policies");
        model.addAttribute("title", "Trust score policies");
        return Mono.just("policies_trust_score_policies");
    }

    @GetMapping("/policies/trust-decision-policies")
    @PreAuthorize("hasAnyRole('PRODUCT_ADMIN','TENANT_ADMIN')")
    public Mono<String> policiesTrustDecisionPolicies(Model model) {
        model.addAttribute("activePage", "policies");
        model.addAttribute("activePolicy", "trust-decision-policies");
        model.addAttribute("title", "Trust decision policies");
        return Mono.just("policies_trust_decision_policies");
    }

    @GetMapping("/policies/remediation-rules")
    @PreAuthorize("hasAnyRole('PRODUCT_ADMIN','TENANT_ADMIN')")
    public Mono<String> policiesRemediationRules(Model model) {
        model.addAttribute("activePage", "policies");
        model.addAttribute("activePolicy", "remediation-rules");
        model.addAttribute("title", "Remediation rules");
        return Mono.just("policies_remediation_rules");
    }

    @GetMapping("/policies/rule-remediation-mappings")
    @PreAuthorize("hasAnyRole('PRODUCT_ADMIN','TENANT_ADMIN')")
    public Mono<String> policiesRuleRemediationMappings(Model model) {
        model.addAttribute("activePage", "policies");
        model.addAttribute("activePolicy", "rule-remediation-mappings");
        model.addAttribute("title", "Rule remediation mappings");
        return Mono.just("policies_rule_remediation_mappings");
    }

    @GetMapping("/policies/audit-trail")
    @PreAuthorize("hasAnyRole('PRODUCT_ADMIN','TENANT_ADMIN','AUDITOR')")
    public Mono<String> policiesAuditTrail(Model model) {
        model.addAttribute("activePage", "policies");
        model.addAttribute("activePolicy", "audit-trail");
        model.addAttribute("title", "Policy audit trail");
        return Mono.just("policies_audit_trail");
    }

    @GetMapping("/catalog/applications")
    @PreAuthorize("hasRole('PRODUCT_ADMIN')")
    public Mono<String> catalogApplications(Model model) {
        model.addAttribute("activePage", "catalog");
        model.addAttribute("title", "Application catalog");
        return Mono.just("catalog_applications");
    }

    @GetMapping("/lookups")
    @PreAuthorize("hasRole('PRODUCT_ADMIN')")
    public Mono<String> lookups(Model model) {
        model.addAttribute("activePage", "lookups");
        model.addAttribute("title", "Lookups");
        return Mono.just("lookups_admin");
    }

    @GetMapping("/tenants")
    @PreAuthorize("hasRole('PRODUCT_ADMIN')")
    public Mono<String> tenants(Model model) {
        model.addAttribute("activePage", "tenants");
        model.addAttribute("title", "Tenants");
        return Mono.just("tenants");
    }

    @GetMapping("/users")
    @PreAuthorize("hasAnyRole('PRODUCT_ADMIN','TENANT_ADMIN')")
    public Mono<String> users(Model model) {
        model.addAttribute("activePage", "users");
        model.addAttribute("title", "Users");
        return Mono.just("users");
    }

    @GetMapping("/reports")
    @PreAuthorize("hasAnyRole('PRODUCT_ADMIN','TENANT_ADMIN')")
    public Mono<String> reports(Model model) {
        model.addAttribute("activePage", "reports");
        model.addAttribute("title", "Reports");
        return Mono.just("reports");
    }

    @GetMapping("/os-lifecycle")
    @PreAuthorize("hasRole('PRODUCT_ADMIN')")
    public Mono<String> osLifecycle(Model model) {
        model.addAttribute("activePage", "os-lifecycle");
        model.addAttribute("title", "OS lifecycle");
        return Mono.just("os_lifecycle");
    }

    @GetMapping("/change-password")
    public Mono<String> changePassword(Model model) {
        model.addAttribute("activePage", "account");
        model.addAttribute("title", "Change password");
        return Mono.just("change_password");
    }
}
