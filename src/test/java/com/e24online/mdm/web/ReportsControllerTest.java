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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportsControllerTest {

    @Mock
    private RemediationReportingService remediationReportingService;

    @Mock
    private FleetReportingService fleetReportingService;

    @Mock
    private AuthenticatedRequestContext requestContext;

    @Mock
    private Authentication authentication;

    private ReportsController controller;

    @BeforeEach
    void setUp() {
        controller = new ReportsController(
                remediationReportingService,
                fleetReportingService,
                requestContext
        );
    }

    @Test
    void remediationEndpoints_resolveTenantAndDelegate() {
        UserPrincipal principal = new UserPrincipal(1L, "tenant-admin", "TENANT_ADMIN", 9L);
        RemediationFleetSummaryResponse summary = new RemediationFleetSummaryResponse(
                "tenant-a", 10L, 5L, 5L, 3L, 2L, 1L, 5L, OffsetDateTime.now()
        );
        DataTableResponse<Map<String, Object>> table = new DataTableResponse<>(
                4,
                10L,
                8L,
                List.of(Map.of("id", 11L, "tenant_id", "tenant-a"))
        );

        when(requestContext.requireUserPrincipal(authentication)).thenReturn(principal);
        when(requestContext.resolveOptionalTenantId(authentication, null)).thenReturn(Mono.just("tenant-a"));
        when(remediationReportingService.getRemediationSummary(principal, "tenant-a")).thenReturn(Mono.just(summary));
        when(remediationReportingService.getRemediationTable(principal, "tenant-a", 4, 0, 25, "OPEN", "dev", "status_updated_at", "desc"))
                .thenReturn(Mono.just(table));

        RemediationFleetSummaryResponse summaryResponse = controller.getRemediationSummary(authentication, null).block();
        DataTableResponse<Map<String, Object>> tableResponse = controller
                .getRemediationTable(authentication, null, 4, 0, 25, "OPEN", "dev", "status_updated_at", "desc")
                .block();

        assertEquals(summary, summaryResponse);
        assertNotNull(tableResponse);
        assertEquals(4, tableResponse.draw());
        assertEquals(1, tableResponse.data().size());
    }

    @Test
    void fleetEndpoints_resolveTenantAndDelegate() {
        UserPrincipal principal = new UserPrincipal(1L, "tenant-admin", "TENANT_ADMIN", 9L);
        OffsetDateTime now = OffsetDateTime.now();
        FleetOperationalSummaryResponse summary = new FleetOperationalSummaryResponse(
                "tenant-a", 72, 25L, 7L, 4L, 1L, 3L, 18L, 2L, 1L, 4L
        );
        DataTableResponse<Map<String, Object>> staleTable = new DataTableResponse<>(
                2,
                7L,
                3L,
                List.of(Map.of("id", 11L, "tenant_id", "tenant-a", "device_external_id", "dev-1"))
        );
        List<TopFailingRuleResponse> topRules = List.of(new TopFailingRuleResponse(
                15L, "RULE-15", "OS", "Outdated OS", "BLOCK", 4L, 2L, 5L, now
        ));
        List<TopRiskyApplicationResponse> riskyApps = List.of(new TopRiskyApplicationResponse(
                "AnyDesk", "com.anydesk", "AnyDesk", "WINDOWS", "REMOTE_ACCESS", 3L, 1L, 3L, now
        ));
        List<ScoreTrendPointResponse> scoreTrend = List.of(new ScoreTrendPointResponse(
                LocalDate.now().minusDays(1), 11L, 8L, 73.5, 6L, 2L, 1L, 2L
        ));
        List<AgentVersionDistributionResponse> agentVersions = List.of(new AgentVersionDistributionResponse(
                "6.2.1", "SUPPORTED_WITH_WARNINGS", 9L, 7L, now
        ));
        List<AgentCapabilityCoverageResponse> capabilities = List.of(new AgentCapabilityCoverageResponse(
                "payload_ack", 6L, now
        ));

        when(requestContext.requireUserPrincipal(authentication)).thenReturn(principal);
        when(requestContext.resolveOptionalTenantId(authentication, null)).thenReturn(Mono.just("tenant-a"));
        when(fleetReportingService.getFleetSummary(principal, "tenant-a", 72)).thenReturn(Mono.just(summary));
        when(fleetReportingService.getStaleDevicesTable(principal, "tenant-a", 2, 0, 25, 72, "dev", "latest_seen_at", "asc"))
                .thenReturn(Mono.just(staleTable));
        when(fleetReportingService.getTopFailingRules(principal, "tenant-a", 5)).thenReturn(Mono.just(topRules));
        when(fleetReportingService.getTopRiskyApplications(principal, "tenant-a", 6)).thenReturn(Mono.just(riskyApps));
        when(fleetReportingService.getScoreTrend(principal, "tenant-a", 14)).thenReturn(Mono.just(scoreTrend));
        when(fleetReportingService.getAgentVersionDistribution(principal, "tenant-a", 8)).thenReturn(Mono.just(agentVersions));
        when(fleetReportingService.getAgentCapabilityCoverage(principal, "tenant-a", 8)).thenReturn(Mono.just(capabilities));

        FleetOperationalSummaryResponse summaryResponse = controller
                .getFleetSummary(authentication, null, 72)
                .block();
        DataTableResponse<Map<String, Object>> tableResponse = controller
                .getStaleDevicesTable(authentication, null, 2, 0, 25, 72, "dev", "latest_seen_at", "asc")
                .block();
        List<TopFailingRuleResponse> topRulesResponse = controller
                .getTopFailingRules(authentication, null, 5)
                .block();
        List<TopRiskyApplicationResponse> riskyAppsResponse = controller
                .getTopRiskyApplications(authentication, null, 6)
                .block();
        List<ScoreTrendPointResponse> scoreTrendResponse = controller
                .getScoreTrend(authentication, null, 14)
                .block();
        List<AgentVersionDistributionResponse> agentVersionsResponse = controller
                .getAgentVersionDistribution(authentication, null, 8)
                .block();
        List<AgentCapabilityCoverageResponse> capabilitiesResponse = controller
                .getAgentCapabilityCoverage(authentication, null, 8)
                .block();

        assertEquals(summary, summaryResponse);
        assertNotNull(tableResponse);
        assertEquals(2, tableResponse.draw());
        assertEquals(1, tableResponse.data().size());
        assertEquals(topRules, topRulesResponse);
        assertEquals(riskyApps, riskyAppsResponse);
        assertEquals(scoreTrend, scoreTrendResponse);
        assertEquals(agentVersions, agentVersionsResponse);
        assertEquals(capabilities, capabilitiesResponse);
        verify(fleetReportingService).getFleetSummary(principal, "tenant-a", 72);
        verify(fleetReportingService).getStaleDevicesTable(principal, "tenant-a", 2, 0, 25, 72, "dev", "latest_seen_at", "asc");
        verify(fleetReportingService).getTopFailingRules(principal, "tenant-a", 5);
        verify(fleetReportingService).getTopRiskyApplications(principal, "tenant-a", 6);
        verify(fleetReportingService).getScoreTrend(principal, "tenant-a", 14);
        verify(fleetReportingService).getAgentVersionDistribution(principal, "tenant-a", 8);
        verify(fleetReportingService).getAgentCapabilityCoverage(principal, "tenant-a", 8);
    }

    @Test
    void productAdminEndpoints_allowGlobalScopeWhenTenantHeaderMissing() {
        UserPrincipal principal = new UserPrincipal(1L, "admin", "PRODUCT_ADMIN", null);
        FleetOperationalSummaryResponse summary = new FleetOperationalSummaryResponse(
                "", 72, 25L, 7L, 4L, 1L, 3L, 18L, 2L, 1L, 4L
        );
        RemediationFleetSummaryResponse remediationSummary = new RemediationFleetSummaryResponse(
                "", 10L, 5L, 5L, 3L, 2L, 1L, 5L, OffsetDateTime.now()
        );

        when(requestContext.requireUserPrincipal(authentication)).thenReturn(principal);
        when(requestContext.resolveOptionalTenantId(authentication, null)).thenReturn(Mono.empty());
        when(fleetReportingService.getFleetSummary(principal, "", 72)).thenReturn(Mono.just(summary));
        when(remediationReportingService.getRemediationSummary(principal, "")).thenReturn(Mono.just(remediationSummary));

        FleetOperationalSummaryResponse fleetResponse = controller
                .getFleetSummary(authentication, null, 72)
                .block();
        RemediationFleetSummaryResponse remediationResponse = controller
                .getRemediationSummary(authentication, null)
                .block();

        assertEquals(summary, fleetResponse);
        assertEquals(remediationSummary, remediationResponse);
        verify(fleetReportingService).getFleetSummary(principal, "", 72);
        verify(remediationReportingService).getRemediationSummary(principal, "");
    }
}
