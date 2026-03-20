package com.e24online.mdm.web;

import com.e24online.mdm.records.reports.AgentCapabilityCoverageResponse;
import com.e24online.mdm.records.reports.AgentVersionDistributionResponse;
import com.e24online.mdm.records.reports.FleetOperationalSummaryResponse;
import com.e24online.mdm.records.reports.RemediationFleetSummaryResponse;
import com.e24online.mdm.records.reports.ScoreTrendPointResponse;
import com.e24online.mdm.records.reports.TopFailingRuleResponse;
import com.e24online.mdm.records.reports.TopRiskyApplicationResponse;
import com.e24online.mdm.records.ui.DataTableResponse;
import com.e24online.mdm.records.user.UserPrincipal;
import com.e24online.mdm.service.FleetReportingService;
import com.e24online.mdm.service.RemediationReportingService;
import com.e24online.mdm.web.security.AuthenticatedRequestContext;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("${api.version.prefix:v1}/reports")
@PreAuthorize("hasAnyRole('PRODUCT_ADMIN','TENANT_ADMIN')")
public class ReportsController {

    private final RemediationReportingService remediationReportingService;
    private final FleetReportingService fleetReportingService;
    private final AuthenticatedRequestContext requestContext;

    public ReportsController(RemediationReportingService remediationReportingService,
                             FleetReportingService fleetReportingService,
                             AuthenticatedRequestContext requestContext) {
        this.remediationReportingService = remediationReportingService;
        this.fleetReportingService = fleetReportingService;
        this.requestContext = requestContext;
    }

    @GetMapping("/remediation/summary")
    public Mono<RemediationFleetSummaryResponse> getRemediationSummary(
            Authentication authentication,
            @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId
    ) {
        UserPrincipal principal = requestContext.requireUserPrincipal(authentication);
        return resolveTenantId(authentication, tenantId)
                .flatMap(resolvedTenantId -> remediationReportingService.getRemediationSummary(principal, resolvedTenantId));
    }

    @GetMapping("/fleet/summary")
    public Mono<FleetOperationalSummaryResponse> getFleetSummary(
            Authentication authentication,
            @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId,
            @RequestParam(name = "stale_after_hours", required = false) Integer staleAfterHours
    ) {
        UserPrincipal principal = requestContext.requireUserPrincipal(authentication);
        return resolveTenantId(authentication, tenantId)
                .flatMap(resolvedTenantId -> fleetReportingService.getFleetSummary(
                        principal,
                        resolvedTenantId,
                        staleAfterHours
                ));
    }

    @GetMapping("/fleet/stale-devices/table")
    public Mono<DataTableResponse<Map<String, Object>>> getStaleDevicesTable(
            Authentication authentication,
            @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId,
            @RequestParam(name = "draw", defaultValue = "0") int draw,
            @RequestParam(name = "start", defaultValue = "0") int start,
            @RequestParam(name = "length", defaultValue = "25") int length,
            @RequestParam(name = "stale_after_hours", required = false) Integer staleAfterHours,
            @RequestParam(name = "search", required = false) String search,
            @RequestParam(name = "sort_by", required = false) String sortBy,
            @RequestParam(name = "sort_dir", required = false) String sortDir
    ) {
        UserPrincipal principal = requestContext.requireUserPrincipal(authentication);
        return resolveTenantId(authentication, tenantId)
                .flatMap(resolvedTenantId -> fleetReportingService.getStaleDevicesTable(
                        principal,
                        resolvedTenantId,
                        draw,
                        start,
                        length,
                        staleAfterHours,
                        search,
                        sortBy,
                        sortDir
                ));
    }

    @GetMapping("/fleet/top-failing-rules")
    public Mono<List<TopFailingRuleResponse>> getTopFailingRules(
            Authentication authentication,
            @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId,
            @RequestParam(name = "limit", required = false) Integer limit
    ) {
        UserPrincipal principal = requestContext.requireUserPrincipal(authentication);
        return resolveTenantId(authentication, tenantId)
                .flatMap(resolvedTenantId -> fleetReportingService.getTopFailingRules(
                        principal,
                        resolvedTenantId,
                        limit
                ));
    }

    @GetMapping("/fleet/top-risky-applications")
    public Mono<List<TopRiskyApplicationResponse>> getTopRiskyApplications(
            Authentication authentication,
            @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId,
            @RequestParam(name = "limit", required = false) Integer limit
    ) {
        UserPrincipal principal = requestContext.requireUserPrincipal(authentication);
        return resolveTenantId(authentication, tenantId)
                .flatMap(resolvedTenantId -> fleetReportingService.getTopRiskyApplications(
                        principal,
                        resolvedTenantId,
                        limit
                ));
    }

    @GetMapping("/fleet/score-trend")
    public Mono<List<ScoreTrendPointResponse>> getScoreTrend(
            Authentication authentication,
            @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId,
            @RequestParam(name = "days", required = false) Integer days
    ) {
        UserPrincipal principal = requestContext.requireUserPrincipal(authentication);
        return resolveTenantId(authentication, tenantId)
                .flatMap(resolvedTenantId -> fleetReportingService.getScoreTrend(
                        principal,
                        resolvedTenantId,
                        days
                ));
    }

    @GetMapping("/fleet/agent-versions")
    public Mono<List<AgentVersionDistributionResponse>> getAgentVersionDistribution(
            Authentication authentication,
            @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId,
            @RequestParam(name = "limit", required = false) Integer limit
    ) {
        UserPrincipal principal = requestContext.requireUserPrincipal(authentication);
        return resolveTenantId(authentication, tenantId)
                .flatMap(resolvedTenantId -> fleetReportingService.getAgentVersionDistribution(
                        principal,
                        resolvedTenantId,
                        limit
                ));
    }

    @GetMapping("/fleet/agent-capabilities")
    public Mono<List<AgentCapabilityCoverageResponse>> getAgentCapabilityCoverage(
            Authentication authentication,
            @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId,
            @RequestParam(name = "limit", required = false) Integer limit
    ) {
        UserPrincipal principal = requestContext.requireUserPrincipal(authentication);
        return resolveTenantId(authentication, tenantId)
                .flatMap(resolvedTenantId -> fleetReportingService.getAgentCapabilityCoverage(
                        principal,
                        resolvedTenantId,
                        limit
                ));
    }

    @GetMapping("/remediation/table")
    public Mono<DataTableResponse<Map<String, Object>>> getRemediationTable(
            Authentication authentication,
            @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId,
            @RequestParam(name = "draw", defaultValue = "0") int draw,
            @RequestParam(name = "start", defaultValue = "0") int start,
            @RequestParam(name = "length", defaultValue = "25") int length,
            @RequestParam(name = "status_view", required = false) String statusView,
            @RequestParam(name = "search", required = false) String search,
            @RequestParam(name = "sort_by", required = false) String sortBy,
            @RequestParam(name = "sort_dir", required = false) String sortDir
    ) {
        UserPrincipal principal = requestContext.requireUserPrincipal(authentication);
        return resolveTenantId(authentication, tenantId)
                .flatMap(resolvedTenantId -> remediationReportingService.getRemediationTable(
                        principal,
                        resolvedTenantId,
                        draw,
                        start,
                        length,
                        statusView,
                        search,
                        sortBy,
                        sortDir
                ));
    }

    private Mono<String> resolveTenantId(Authentication authentication, String tenantId) {
        return requestContext.resolveOptionalTenantId(authentication, tenantId)
                .defaultIfEmpty("");
    }

}
