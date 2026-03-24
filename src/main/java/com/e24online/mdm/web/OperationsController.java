package com.e24online.mdm.web;

import com.e24online.mdm.records.operations.PipelineDailyTrendResponse;
import com.e24online.mdm.records.operations.PipelineFailureCategoryResponse;
import com.e24online.mdm.records.operations.PipelineOperabilitySummaryResponse;
import com.e24online.mdm.records.operations.QueueHealthSummaryResponse;
import com.e24online.mdm.records.ui.DataTableResponse;
import com.e24online.mdm.service.OperationsReportingService;
import com.e24online.mdm.service.QueueHealthService;
import com.e24online.mdm.web.security.AuthenticatedRequestContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("${api.version.prefix:v1}/operations")
@PreAuthorize("hasRole('PRODUCT_ADMIN')")
public class OperationsController {

    private final QueueHealthService queueHealthService;
    private final OperationsReportingService operationsReportingService;
    private final AuthenticatedRequestContext requestContext;

    public OperationsController(QueueHealthService queueHealthService,
                                OperationsReportingService operationsReportingService,
                                AuthenticatedRequestContext requestContext) {
        this.queueHealthService = queueHealthService;
        this.operationsReportingService = operationsReportingService;
        this.requestContext = requestContext;
    }

    @GetMapping("/queues/summary")
    public Mono<QueueHealthSummaryResponse> getQueueHealthSummary() {
        return queueHealthService.getQueueHealthSummary();
    }

    @GetMapping("/pipeline/summary")
    public Mono<PipelineOperabilitySummaryResponse> getPipelineSummary(
            Authentication authentication,
            @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId
    ) {
        return resolveTenantId(authentication, tenantId)
                .flatMap(operationsReportingService::getPipelineSummary);
    }

    @GetMapping("/pipeline/failure-categories")
    public Mono<List<PipelineFailureCategoryResponse>> getFailureCategories(
            Authentication authentication,
            @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId,
            @RequestParam(name = "days", required = false) Integer days,
            @RequestParam(name = "limit", required = false) Integer limit
    ) {
        return resolveTenantId(authentication, tenantId)
                .flatMap(resolvedTenantId -> operationsReportingService.getFailureCategories(
                        resolvedTenantId,
                        days,
                        limit
                ));
    }

    @GetMapping("/pipeline/trend")
    public Mono<List<PipelineDailyTrendResponse>> getPipelineTrend(
            Authentication authentication,
            @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId,
            @RequestParam(name = "days", required = false) Integer days
    ) {
        return resolveTenantId(authentication, tenantId)
                .flatMap(resolvedTenantId -> operationsReportingService.getPipelineTrend(
                        resolvedTenantId,
                        days
                ));
    }

    @GetMapping("/pipeline/failed-payloads/table")
    public Mono<DataTableResponse<Map<String, Object>>> getFailedPayloadsTable(
            Authentication authentication,
            @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId,
            @RequestParam(name = "draw", defaultValue = "0") int draw,
            @RequestParam(name = "start", defaultValue = "0") int start,
            @RequestParam(name = "length", defaultValue = "25") int length,
            @RequestParam(name = "days", required = false) Integer days,
            @RequestParam(name = "search", required = false) String search,
            @RequestParam(name = "sort_by", required = false) String sortBy,
            @RequestParam(name = "sort_dir", required = false) String sortDir
    ) {
        return resolveTenantId(authentication, tenantId)
                .flatMap(resolvedTenantId -> operationsReportingService.getFailedPayloadsTable(
                        resolvedTenantId,
                        draw,
                        start,
                        length,
                        days,
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
