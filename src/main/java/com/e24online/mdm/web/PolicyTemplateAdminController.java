package com.e24online.mdm.web;

import com.e24online.mdm.records.PolicyTemplateApplyReport;
import com.e24online.mdm.service.PolicyTemplateMaintenanceService;
import com.e24online.mdm.web.security.AuthenticatedRequestContext;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("${api.version.prefix:v1}/admin/policy-templates")
@PreAuthorize("hasRole('PRODUCT_ADMIN')")
public class PolicyTemplateAdminController {

    private final PolicyTemplateMaintenanceService policyTemplateMaintenanceService;
    private final AuthenticatedRequestContext requestContext;

    public PolicyTemplateAdminController(PolicyTemplateMaintenanceService policyTemplateMaintenanceService,
                                         AuthenticatedRequestContext requestContext) {
        this.policyTemplateMaintenanceService = policyTemplateMaintenanceService;
        this.requestContext = requestContext;
    }

    @PostMapping("/apply-production-pack")
    public Mono<PolicyTemplateApplyReport> applyProductionPack(
            Authentication authentication,
            @RequestParam(name = "includeTenantScopes", defaultValue = "true") boolean includeTenantScopes,
            @RequestParam(name = "clearPolicyAudit", defaultValue = "false") boolean clearPolicyAudit
    ) {
        String actor = requestContext.resolveActor(authentication);
        return policyTemplateMaintenanceService.applyProductionPack(actor, includeTenantScopes, clearPolicyAudit);
    }
}
