package com.e24online.mdm.web;

import com.e24online.mdm.records.ui.DataTablePage;
import com.e24online.mdm.records.ui.DataTableResponse;
import com.e24online.mdm.service.BlockingDb;
import com.e24online.mdm.service.UiDataTableService;
import com.e24online.mdm.web.security.AuthenticatedRequestContext;
import com.e24online.mdm.records.user.UserPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.function.Supplier;

@RestController
@RequestMapping("${api.version.prefix:v1}/ui/datatables")
public class UiDataTablesController {

    private final UiDataTableService dataTableService;
    private final BlockingDb blockingDb;
    private final AuthenticatedRequestContext requestContext;

    public UiDataTablesController(UiDataTableService dataTableService,
                                  BlockingDb blockingDb,
                                  AuthenticatedRequestContext requestContext) {
        this.dataTableService = dataTableService;
        this.blockingDb = blockingDb;
        this.requestContext = requestContext;
    }

    @GetMapping("/system-rules")
    @PreAuthorize("hasRole('PRODUCT_ADMIN')")
    public Mono<DataTableResponse<Map<String, Object>>> systemRules(
            Authentication authentication,
            @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId,
            @RequestParam(name = "draw", defaultValue = "0") int draw,
            @RequestParam(name = "start", defaultValue = "0") int start,
            @RequestParam(name = "length", defaultValue = "25") int length,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "search", required = false) String search,
            @RequestParam(name = "sort_by", required = false) String sortBy,
            @RequestParam(name = "sort_dir", required = false) String sortDir
    ) {
        return requestContext.resolveOptionalTenantId(authentication, tenantId)
                .defaultIfEmpty("")
                .flatMap(resolvedTenantId -> run(() -> dataTableService.systemRules(
                        draw,
                        start,
                        length,
                        normalizeOptionalTenantId(resolvedTenantId),
                        status,
                        search,
                        sortBy,
                        sortDir
                )));
    }

    @GetMapping("/reject-apps")
    @PreAuthorize("hasRole('PRODUCT_ADMIN')")
    public Mono<DataTableResponse<Map<String, Object>>> rejectApps(
            Authentication authentication,
            @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId,
            @RequestParam(name = "draw", defaultValue = "0") int draw,
            @RequestParam(name = "start", defaultValue = "0") int start,
            @RequestParam(name = "length", defaultValue = "25") int length,
            @RequestParam(name = "app_os_type", required = false) String osType,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "search", required = false) String search,
            @RequestParam(name = "sort_by", required = false) String sortBy,
            @RequestParam(name = "sort_dir", required = false) String sortDir
    ) {
        return requestContext.resolveOptionalTenantId(authentication, tenantId)
                .defaultIfEmpty("")
                .flatMap(resolvedTenantId -> run(() -> dataTableService.rejectApps(
                        draw,
                        start,
                        length,
                        normalizeOptionalTenantId(resolvedTenantId),
                        osType,
                        status,
                        search,
                        sortBy,
                        sortDir
                )));
    }

    @GetMapping("/trust-score-policies")
    @PreAuthorize("hasRole('PRODUCT_ADMIN')")
    public Mono<DataTableResponse<Map<String, Object>>> trustScorePolicies(
            Authentication authentication,
            @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId,
            @RequestParam(name = "draw", defaultValue = "0") int draw,
            @RequestParam(name = "start", defaultValue = "0") int start,
            @RequestParam(name = "length", defaultValue = "25") int length,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "search", required = false) String search,
            @RequestParam(name = "sort_by", required = false) String sortBy,
            @RequestParam(name = "sort_dir", required = false) String sortDir
    ) {
        return requestContext.resolveOptionalTenantId(authentication, tenantId)
                .defaultIfEmpty("")
                .flatMap(resolvedTenantId -> run(() -> dataTableService.trustScorePolicies(
                        draw,
                        start,
                        length,
                        normalizeOptionalTenantId(resolvedTenantId),
                        status,
                        search,
                        sortBy,
                        sortDir
                )));
    }

    @GetMapping("/trust-decision-policies")
    @PreAuthorize("hasRole('PRODUCT_ADMIN')")
    public Mono<DataTableResponse<Map<String, Object>>> trustDecisionPolicies(
            Authentication authentication,
            @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId,
            @RequestParam(name = "draw", defaultValue = "0") int draw,
            @RequestParam(name = "start", defaultValue = "0") int start,
            @RequestParam(name = "length", defaultValue = "25") int length,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "search", required = false) String search,
            @RequestParam(name = "sort_by", required = false) String sortBy,
            @RequestParam(name = "sort_dir", required = false) String sortDir
    ) {
        return requestContext.resolveOptionalTenantId(authentication, tenantId)
                .defaultIfEmpty("")
                .flatMap(resolvedTenantId -> run(() -> dataTableService.trustDecisionPolicies(
                        draw,
                        start,
                        length,
                        normalizeOptionalTenantId(resolvedTenantId),
                        status,
                        search,
                        sortBy,
                        sortDir
                )));
    }

    @GetMapping("/remediation-rules")
    @PreAuthorize("hasRole('PRODUCT_ADMIN')")
    public Mono<DataTableResponse<Map<String, Object>>> remediationRules(
            Authentication authentication,
            @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId,
            @RequestParam(name = "draw", defaultValue = "0") int draw,
            @RequestParam(name = "start", defaultValue = "0") int start,
            @RequestParam(name = "length", defaultValue = "25") int length,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "search", required = false) String search,
            @RequestParam(name = "sort_by", required = false) String sortBy,
            @RequestParam(name = "sort_dir", required = false) String sortDir
    ) {
        return requestContext.resolveOptionalTenantId(authentication, tenantId)
                .defaultIfEmpty("")
                .flatMap(resolvedTenantId -> run(() -> dataTableService.remediationRules(
                        draw,
                        start,
                        length,
                        normalizeOptionalTenantId(resolvedTenantId),
                        status,
                        search,
                        sortBy,
                        sortDir
                )));
    }

    @GetMapping("/rule-remediation-mappings")
    @PreAuthorize("hasRole('PRODUCT_ADMIN')")
    public Mono<DataTableResponse<Map<String, Object>>> ruleRemediationMappings(
            Authentication authentication,
            @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId,
            @RequestParam(name = "draw", defaultValue = "0") int draw,
            @RequestParam(name = "start", defaultValue = "0") int start,
            @RequestParam(name = "length", defaultValue = "25") int length,
            @RequestParam(name = "source_type", required = false) String sourceType,
            @RequestParam(name = "search", required = false) String search,
            @RequestParam(name = "sort_by", required = false) String sortBy,
            @RequestParam(name = "sort_dir", required = false) String sortDir
    ) {
        return requestContext.resolveOptionalTenantId(authentication, tenantId)
                .defaultIfEmpty("")
                .flatMap(resolvedTenantId -> run(() -> dataTableService.ruleRemediationMappings(
                        draw,
                        start,
                        length,
                        normalizeOptionalTenantId(resolvedTenantId),
                        sourceType,
                        search,
                        sortBy,
                        sortDir
                )));
    }

    @GetMapping("/policy-audit")
    @PreAuthorize("hasAnyRole('PRODUCT_ADMIN','AUDITOR')")
    public Mono<DataTableResponse<Map<String, Object>>> policyAudit(
            Authentication authentication,
            @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId,
            @RequestParam(name = "draw", defaultValue = "0") int draw,
            @RequestParam(name = "start", defaultValue = "0") int start,
            @RequestParam(name = "length", defaultValue = "25") int length,
            @RequestParam(name = "policy_type", required = false) String policyType,
            @RequestParam(name = "operation", required = false) String operation,
            @RequestParam(name = "actor", required = false) String actor,
            @RequestParam(name = "search", required = false) String search,
            @RequestParam(name = "sort_by", required = false) String sortBy,
            @RequestParam(name = "sort_dir", required = false) String sortDir
    ) {
        return requestContext.resolveOptionalTenantId(authentication, tenantId)
                .defaultIfEmpty("")
                .flatMap(resolvedTenantId -> run(() -> dataTableService.policyAudit(
                        draw,
                        start,
                        length,
                        normalizeOptionalTenantId(resolvedTenantId),
                        policyType,
                        operation,
                        actor,
                        search,
                        sortBy,
                        sortDir
                )));
    }

    @GetMapping("/audit-events")
    @PreAuthorize("hasAnyRole('PRODUCT_ADMIN','TENANT_ADMIN','AUDITOR')")
    public Mono<DataTableResponse<Map<String, Object>>> auditEvents(
            Authentication authentication,
            @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId,
            @RequestParam(name = "draw", defaultValue = "0") int draw,
            @RequestParam(name = "start", defaultValue = "0") int start,
            @RequestParam(name = "length", defaultValue = "25") int length,
            @RequestParam(name = "event_category", required = false) String eventCategory,
            @RequestParam(name = "event_type", required = false) String eventType,
            @RequestParam(name = "action", required = false) String action,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "actor", required = false) String actor,
            @RequestParam(name = "search", required = false) String search,
            @RequestParam(name = "sort_by", required = false) String sortBy,
            @RequestParam(name = "sort_dir", required = false) String sortDir
    ) {
        return requestContext.resolveOptionalTenantId(authentication, tenantId)
                .defaultIfEmpty("")
                .flatMap(resolvedTenantId -> run(() -> dataTableService.auditEvents(
                        draw,
                        start,
                        length,
                        normalizeOptionalTenantId(resolvedTenantId),
                        eventCategory,
                        eventType,
                        action,
                        status,
                        actor,
                        search,
                        sortBy,
                        sortDir
                )));
    }

    @GetMapping("/catalog/applications")
    @PreAuthorize("hasAnyRole('PRODUCT_ADMIN','TENANT_ADMIN')")
    public Mono<DataTableResponse<Map<String, Object>>> catalogApplications(
            @RequestParam(name = "draw", defaultValue = "0") int draw,
            @RequestParam(name = "start", defaultValue = "0") int start,
            @RequestParam(name = "length", defaultValue = "25") int length,
            @RequestParam(name = "os_type", required = false) String osType,
            @RequestParam(name = "search", required = false) String search,
            @RequestParam(name = "sort_by", required = false) String sortBy,
            @RequestParam(name = "sort_dir", required = false) String sortDir
    ) {
        return run(() -> dataTableService.catalogApplications(draw, start, length, osType, search, sortBy, sortDir));
    }

    @GetMapping("/os-lifecycle")
    @PreAuthorize("hasRole('PRODUCT_ADMIN')")
    public Mono<DataTableResponse<Map<String, Object>>> osLifecycle(
            @RequestParam(name = "draw", defaultValue = "0") int draw,
            @RequestParam(name = "start", defaultValue = "0") int start,
            @RequestParam(name = "length", defaultValue = "25") int length,
            @RequestParam(name = "platform_code", required = false) String platformCode,
            @RequestParam(name = "search", required = false) String search,
            @RequestParam(name = "sort_by", required = false) String sortBy,
            @RequestParam(name = "sort_dir", required = false) String sortDir
    ) {
        return run(() -> dataTableService.osLifecycle(draw, start, length, platformCode, search, sortBy, sortDir));
    }

    @GetMapping("/lookups")
    @PreAuthorize("hasAnyRole('PRODUCT_ADMIN','TENANT_ADMIN')")
    public Mono<DataTableResponse<Map<String, Object>>> lookupValues(
            @RequestParam(name = "draw", defaultValue = "0") int draw,
            @RequestParam(name = "start", defaultValue = "0") int start,
            @RequestParam(name = "length", defaultValue = "25") int length,
            @RequestParam(name = "lookup_type", required = false) String lookupType,
            @RequestParam(name = "search", required = false) String search,
            @RequestParam(name = "sort_by", required = false) String sortBy,
            @RequestParam(name = "sort_dir", required = false) String sortDir
    ) {
        return run(() -> dataTableService.lookupValues(draw, start, length, lookupType, search, sortBy, sortDir));
    }

    @GetMapping("/system-rule-conditions")
    @PreAuthorize("hasRole('PRODUCT_ADMIN')")
    public Mono<DataTableResponse<Map<String, Object>>> systemRuleConditions(
            Authentication authentication,
            @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId,
            @RequestParam(name = "draw", defaultValue = "0") int draw,
            @RequestParam(name = "start", defaultValue = "0") int start,
            @RequestParam(name = "length", defaultValue = "25") int length,
            @RequestParam(name = "rule_id") Long ruleId,
            @RequestParam(name = "search", required = false) String search,
            @RequestParam(name = "sort_by", required = false) String sortBy,
            @RequestParam(name = "sort_dir", required = false) String sortDir
    ) {
        return requestContext.resolveOptionalTenantId(authentication, tenantId)
                .defaultIfEmpty("")
                .flatMap(resolvedTenantId -> run(() -> dataTableService.systemRuleConditions(
                        draw,
                        start,
                        length,
                        normalizeOptionalTenantId(resolvedTenantId),
                        ruleId,
                        search,
                        sortBy,
                        sortDir
                )));
    }

    @GetMapping("/device-trust-profiles")
    @PreAuthorize("hasAnyRole('PRODUCT_ADMIN','TENANT_ADMIN','TENANT_USER')")
    public Mono<DataTableResponse<Map<String, Object>>> deviceTrustProfiles(
            Authentication authentication,
            @RequestParam(name = "draw", defaultValue = "0") int draw,
            @RequestParam(name = "start", defaultValue = "0") int start,
            @RequestParam(name = "length", defaultValue = "25") int length,
            @RequestParam(name = "os_type", required = false) String osType,
            @RequestParam(name = "score_band", required = false) String scoreBand,
            @RequestParam(name = "search", required = false) String search,
            @RequestParam(name = "sort_by", required = false) String sortBy,
            @RequestParam(name = "sort_dir", required = false) String sortDir
    ) {
        UserPrincipal principal = requestContext.requireUserPrincipal(authentication);
        if (isProductAdmin(principal)) {
            return run(() -> dataTableService.deviceTrustProfiles(draw, start, length, null, null, osType, scoreBand, search, sortBy, sortDir));
        }
        if (isTenantAdmin(principal)) {
            return requestContext.resolveTenantId(authentication, null)
                    .flatMap(tenantId -> run(() -> dataTableService.deviceTrustProfiles(
                            draw,
                            start,
                            length,
                            tenantId,
                            null,
                            osType,
                            scoreBand,
                            search,
                            sortBy,
                            sortDir
                    )));
        }
        if (isTenantUser(principal)) {
            Long ownerUserId = requirePositive(principal.id(), "user_id");
            return requestContext.resolveTenantId(authentication, null)
                    .flatMap(tenantId -> run(() -> dataTableService.deviceTrustProfiles(
                            draw,
                            start,
                            length,
                            tenantId,
                            ownerUserId,
                            osType,
                            scoreBand,
                            search,
                            sortBy,
                            sortDir
                    )));
        }
        return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "Unsupported role"));
    }

    @GetMapping("/posture-payloads")
    @PreAuthorize("hasAnyRole('PRODUCT_ADMIN','TENANT_ADMIN')")
    public Mono<DataTableResponse<Map<String, Object>>> posturePayloads(
            Authentication authentication,
            @RequestParam(name = "draw", defaultValue = "0") int draw,
            @RequestParam(name = "start", defaultValue = "0") int start,
            @RequestParam(name = "length", defaultValue = "25") int length,
            @RequestParam(name = "tenant_id", required = false) String tenantId,
            @RequestParam(name = "process_status", required = false) String processStatus,
            @RequestParam(name = "search", required = false) String search,
            @RequestParam(name = "sort_by", required = false) String sortBy,
            @RequestParam(name = "sort_dir", required = false) String sortDir
    ) {
        UserPrincipal principal = requestContext.requireUserPrincipal(authentication);
        if (isProductAdmin(principal)) {
            return run(() -> dataTableService.posturePayloads(draw, start, length, tenantId, processStatus, search, sortBy, sortDir));
        }
        if (!isTenantAdmin(principal)) {
            return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "Unsupported role"));
        }
        return requestContext.resolveTenantId(authentication, tenantId)
                .flatMap(resolvedTenantId -> run(() -> dataTableService.posturePayloads(
                        draw,
                        start,
                        length,
                        resolvedTenantId,
                        processStatus,
                        search,
                        sortBy,
                        sortDir
                )));
    }

    @GetMapping("/tenants")
    @PreAuthorize("hasRole('PRODUCT_ADMIN')")
    public Mono<DataTableResponse<Map<String, Object>>> tenants(
            @RequestParam(name = "draw", defaultValue = "0") int draw,
            @RequestParam(name = "start", defaultValue = "0") int start,
            @RequestParam(name = "length", defaultValue = "25") int length,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "search", required = false) String search,
            @RequestParam(name = "sort_by", required = false) String sortBy,
            @RequestParam(name = "sort_dir", required = false) String sortDir
    ) {
        return run(() -> dataTableService.tenants(draw, start, length, status, search, sortBy, sortDir));
    }

    @GetMapping("/users")
    @PreAuthorize("hasAnyRole('PRODUCT_ADMIN','TENANT_ADMIN')")
    public Mono<DataTableResponse<Map<String, Object>>> users(
            Authentication authentication,
            @RequestParam(name = "draw", defaultValue = "0") int draw,
            @RequestParam(name = "start", defaultValue = "0") int start,
            @RequestParam(name = "length", defaultValue = "25") int length,
            @RequestParam(name = "role", required = false) String role,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "search", required = false) String search,
            @RequestParam(name = "sort_by", required = false) String sortBy,
            @RequestParam(name = "sort_dir", required = false) String sortDir
    ) {
        UserPrincipal principal = requestContext.requireUserPrincipal(authentication);
        if (isProductAdmin(principal)) {
            return run(() -> dataTableService.users(draw, start, length, role, status, null, false, search, sortBy, sortDir));
        }
        if (isTenantAdmin(principal)) {
            Long tenantMasterId = principal.tenantId();
            if (tenantMasterId == null || tenantMasterId <= 0L) {
                return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "Tenant scope missing"));
            }
            return run(() -> dataTableService.users(
                    draw,
                    start,
                    length,
                    role,
                    status,
                    tenantMasterId,
                    true,
                    search,
                    sortBy,
                    sortDir
            ));
        }
        return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "Unsupported role"));
    }

    private static DataTableResponse<Map<String, Object>> toResponse(DataTablePage page) {
        return new DataTableResponse<>(page.draw(), page.recordsTotal(), page.recordsFiltered(), page.data());
    }

    private Mono<DataTableResponse<Map<String, Object>>> run(Supplier<DataTablePage> supplier) {
        return blockingDb.mono(() -> toResponse(supplier.get()));
    }

    private boolean isProductAdmin(UserPrincipal principal) {
        return principal != null && "PRODUCT_ADMIN".equalsIgnoreCase(principal.role());
    }

    private boolean isTenantAdmin(UserPrincipal principal) {
        return principal != null && "TENANT_ADMIN".equalsIgnoreCase(principal.role());
    }

    private boolean isTenantUser(UserPrincipal principal) {
        return principal != null && "TENANT_USER".equalsIgnoreCase(principal.role());
    }

    private Long requirePositive(Long value, String fieldName) {
        if (value == null || value <= 0L) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " must be positive");
        }
        return value;
    }

    private String normalizeOptionalTenantId(String tenantId) {
        if (tenantId == null) {
            return null;
        }
        String normalized = tenantId.trim();
        return normalized.isEmpty() ? null : normalized;
    }

}
