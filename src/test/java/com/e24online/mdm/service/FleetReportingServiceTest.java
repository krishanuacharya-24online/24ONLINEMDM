package com.e24online.mdm.service;

import com.e24online.mdm.records.reports.AgentCapabilityCoverageResponse;
import com.e24online.mdm.records.reports.AgentVersionDistributionResponse;
import com.e24online.mdm.records.reports.FleetOperationalSummaryResponse;
import com.e24online.mdm.records.reports.ScoreTrendPointResponse;
import com.e24online.mdm.records.reports.TopFailingRuleResponse;
import com.e24online.mdm.records.reports.TopRiskyApplicationResponse;
import com.e24online.mdm.records.ui.DataTableResponse;
import com.e24online.mdm.records.user.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FleetReportingServiceTest {

    @Mock
    private NamedParameterJdbcTemplate jdbc;

    @Mock
    private RemediationReportingService remediationReportingService;

    private FleetReportingService service;

    @BeforeEach
    void setUp() {
        service = new FleetReportingService(
                jdbc,
                remediationReportingService,
                new BlockingDb(Schedulers.immediate())
        );
    }

    @Test
    void getFleetSummary_mapsAggregateCounts() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("total_devices", 25L);
        row.put("stale_devices", 7L);
        row.put("high_risk_devices", 4L);
        row.put("critical_devices", 1L);
        row.put("lifecycle_risk_devices", 3L);
        row.put("supported_devices", 18L);
        row.put("eol_devices", 2L);
        row.put("eeol_devices", 1L);
        row.put("not_tracked_devices", 4L);

        when(remediationReportingService.assertCanAccessReports(any(), eq("tenant-a"))).thenReturn(Mono.empty());
        when(jdbc.queryForMap(anyString(), any(MapSqlParameterSource.class))).thenReturn(row);

        FleetOperationalSummaryResponse response = service.getFleetSummary(
                new UserPrincipal(2L, "tenant-admin", "TENANT_ADMIN", 9L),
                "tenant-a",
                72
        ).block();

        assertNotNull(response);
        assertEquals("tenant-a", response.scopeTenantId());
        assertEquals(72, response.staleAfterHours());
        assertEquals(25L, response.totalDevices());
        assertEquals(7L, response.staleDevices());
        assertEquals(4L, response.highRiskDevices());
        assertEquals(1L, response.criticalDevices());
        assertEquals(3L, response.lifecycleRiskDevices());
        assertEquals(18L, response.supportedDevices());
        assertEquals(2L, response.eolDevices());
        assertEquals(1L, response.eeolDevices());
        assertEquals(4L, response.notTrackedDevices());
    }

    @Test
    void getFleetSummary_avoidsUntypedNullTenantPredicate() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("total_devices", 0L);
        row.put("stale_devices", 0L);
        row.put("high_risk_devices", 0L);
        row.put("critical_devices", 0L);
        row.put("lifecycle_risk_devices", 0L);
        row.put("supported_devices", 0L);
        row.put("eol_devices", 0L);
        row.put("eeol_devices", 0L);
        row.put("not_tracked_devices", 0L);

        when(remediationReportingService.assertCanAccessReports(any(), eq("tenant-a"))).thenReturn(Mono.empty());
        when(jdbc.queryForMap(anyString(), any(MapSqlParameterSource.class))).thenReturn(row);

        service.getFleetSummary(
                new UserPrincipal(2L, "tenant-admin", "TENANT_ADMIN", 9L),
                "tenant-a",
                72
        ).block();

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).queryForMap(sqlCaptor.capture(), paramsCaptor.capture());

        String sql = sqlCaptor.getValue();
        MapSqlParameterSource params = paramsCaptor.getValue();

        assertFalse(sql.contains(":tenantId IS NULL"));
        assertTrue(sql.contains(":tenantFilterDisabled = TRUE"));
        assertEquals("tenant-a", params.getValue("tenantId"));
        assertEquals(false, params.getValue("tenantFilterDisabled"));
    }

    @Test
    void getStaleDevicesTable_returnsPagedRows() {
        when(remediationReportingService.assertCanAccessReports(any(), eq("tenant-a"))).thenReturn(Mono.empty());
        when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class)))
                .thenReturn(7L, 3L);

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", 501L);
        row.put("tenant_id", "tenant-a");
        row.put("device_external_id", "dev-1");
        row.put("os_type", "WINDOWS");
        row.put("os_name", "Windows 10");
        row.put("current_score", 42);
        row.put("score_band", "HIGH_RISK");
        row.put("posture_status", "NON_COMPLIANT");
        row.put("os_lifecycle_state", "EOL");
        row.put("latest_seen_at", OffsetDateTime.now().minusDays(4));
        when(jdbc.queryForList(anyString(), any(MapSqlParameterSource.class))).thenReturn(List.of(row));

        DataTableResponse<Map<String, Object>> response = service.getStaleDevicesTable(
                new UserPrincipal(2L, "tenant-admin", "TENANT_ADMIN", 9L),
                "tenant-a",
                4,
                0,
                25,
                72,
                "dev",
                "latest_seen_at",
                "asc"
        ).block();

        assertNotNull(response);
        assertEquals(4, response.draw());
        assertEquals(7L, response.recordsTotal());
        assertEquals(3L, response.recordsFiltered());
        assertEquals(1, response.data().size());
        assertEquals("tenant-a", response.data().getFirst().get("tenant_id"));

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForList(sqlCaptor.capture(), any(MapSqlParameterSource.class));
        assertTrue(sqlCaptor.getValue().contains("ORDER BY"));
        assertTrue(sqlCaptor.getValue().contains("latest_seen_at ASC, id DESC"));
    }

    @Test
    void getTopFailingRules_mapsRows() {
        OffsetDateTime now = OffsetDateTime.now();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("rule_id", 15L);
        row.put("rule_code", "RULE-15");
        row.put("rule_tag", "OS");
        row.put("rule_description", "Outdated OS");
        row.put("compliance_action", "BLOCK");
        row.put("impacted_devices", 4L);
        row.put("blocked_devices", 2L);
        row.put("current_match_count", 5L);
        row.put("latest_evaluated_at", now);

        when(remediationReportingService.assertCanAccessReports(any(), eq("tenant-a"))).thenReturn(Mono.empty());
        when(jdbc.queryForList(anyString(), any(MapSqlParameterSource.class))).thenReturn(List.of(row));

        List<TopFailingRuleResponse> response = service.getTopFailingRules(
                new UserPrincipal(2L, "tenant-admin", "TENANT_ADMIN", 9L),
                "tenant-a",
                5
        ).block();

        assertNotNull(response);
        assertEquals(1, response.size());
        assertEquals(15L, response.getFirst().ruleId());
        assertEquals("RULE-15", response.getFirst().ruleCode());
        assertEquals("OS", response.getFirst().ruleTag());
        assertEquals("Outdated OS", response.getFirst().ruleDescription());
        assertEquals("BLOCK", response.getFirst().complianceAction());
        assertEquals(4L, response.getFirst().impactedDevices());
        assertEquals(2L, response.getFirst().blockedDevices());
        assertEquals(5L, response.getFirst().currentMatchCount());
        assertEquals(now, response.getFirst().latestEvaluatedAt());
    }

    @Test
    void getTopRiskyApplications_mapsRows() {
        OffsetDateTime now = OffsetDateTime.now();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("app_name", "AnyDesk");
        row.put("package_id", "com.anydesk");
        row.put("publisher", "AnyDesk");
        row.put("app_os_type", "WINDOWS");
        row.put("policy_tag", "REMOTE_ACCESS");
        row.put("impacted_devices", 3L);
        row.put("blocked_devices", 1L);
        row.put("current_match_count", 3L);
        row.put("latest_evaluated_at", now);

        when(remediationReportingService.assertCanAccessReports(any(), eq("tenant-a"))).thenReturn(Mono.empty());
        when(jdbc.queryForList(anyString(), any(MapSqlParameterSource.class))).thenReturn(List.of(row));

        List<TopRiskyApplicationResponse> response = service.getTopRiskyApplications(
                new UserPrincipal(2L, "tenant-admin", "TENANT_ADMIN", 9L),
                "tenant-a",
                6
        ).block();

        assertNotNull(response);
        assertEquals(1, response.size());
        assertEquals("AnyDesk", response.getFirst().appName());
        assertEquals("com.anydesk", response.getFirst().packageId());
        assertEquals("AnyDesk", response.getFirst().publisher());
        assertEquals("WINDOWS", response.getFirst().appOsType());
        assertEquals("REMOTE_ACCESS", response.getFirst().policyTag());
        assertEquals(3L, response.getFirst().impactedDevices());
        assertEquals(1L, response.getFirst().blockedDevices());
        assertEquals(3L, response.getFirst().currentMatchCount());
        assertEquals(now, response.getFirst().latestEvaluatedAt());
    }

    @Test
    void getScoreTrend_mapsRows() {
        LocalDate bucketDate = LocalDate.now().minusDays(1);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("bucket_date", bucketDate);
        row.put("evaluation_count", 11L);
        row.put("distinct_devices", 8L);
        row.put("average_trust_score", 73.5);
        row.put("allow_count", 6L);
        row.put("notify_count", 2L);
        row.put("quarantine_count", 1L);
        row.put("block_count", 2L);

        when(remediationReportingService.assertCanAccessReports(any(), eq("tenant-a"))).thenReturn(Mono.empty());
        when(jdbc.queryForList(anyString(), any(MapSqlParameterSource.class))).thenReturn(List.of(row));

        List<ScoreTrendPointResponse> response = service.getScoreTrend(
                new UserPrincipal(2L, "tenant-admin", "TENANT_ADMIN", 9L),
                "tenant-a",
                14
        ).block();

        assertNotNull(response);
        assertEquals(1, response.size());
        assertEquals(bucketDate, response.getFirst().bucketDate());
        assertEquals(11L, response.getFirst().evaluationCount());
        assertEquals(8L, response.getFirst().distinctDevices());
        assertEquals(73.5, response.getFirst().averageTrustScore());
        assertEquals(6L, response.getFirst().allowCount());
        assertEquals(2L, response.getFirst().notifyCount());
        assertEquals(1L, response.getFirst().quarantineCount());
        assertEquals(2L, response.getFirst().blockCount());
    }

    @Test
    void getAgentVersionDistribution_mapsRows() {
        OffsetDateTime now = OffsetDateTime.now();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("agent_version", "6.2.1");
        row.put("schema_compatibility_status", "SUPPORTED_WITH_WARNINGS");
        row.put("device_count", 9L);
        row.put("devices_with_capabilities", 7L);
        row.put("latest_capture_time", now);

        when(remediationReportingService.assertCanAccessReports(any(), eq("tenant-a"))).thenReturn(Mono.empty());
        when(jdbc.queryForList(anyString(), any(MapSqlParameterSource.class))).thenReturn(List.of(row));

        List<AgentVersionDistributionResponse> response = service.getAgentVersionDistribution(
                new UserPrincipal(2L, "tenant-admin", "TENANT_ADMIN", 9L),
                "tenant-a",
                8
        ).block();

        assertNotNull(response);
        assertEquals(1, response.size());
        assertEquals("6.2.1", response.getFirst().agentVersion());
        assertEquals("SUPPORTED_WITH_WARNINGS", response.getFirst().schemaCompatibilityStatus());
        assertEquals(9L, response.getFirst().deviceCount());
        assertEquals(7L, response.getFirst().devicesWithCapabilities());
        assertEquals(now, response.getFirst().latestCaptureTime());
    }

    @Test
    void getAgentCapabilityCoverage_mapsRows() {
        OffsetDateTime now = OffsetDateTime.now();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("capability_key", "payload_ack");
        row.put("device_count", 6L);
        row.put("latest_capture_time", now);

        when(remediationReportingService.assertCanAccessReports(any(), eq("tenant-a"))).thenReturn(Mono.empty());
        when(jdbc.queryForList(anyString(), any(MapSqlParameterSource.class))).thenReturn(List.of(row));

        List<AgentCapabilityCoverageResponse> response = service.getAgentCapabilityCoverage(
                new UserPrincipal(2L, "tenant-admin", "TENANT_ADMIN", 9L),
                "tenant-a",
                8
        ).block();

        assertNotNull(response);
        assertEquals(1, response.size());
        assertEquals("payload_ack", response.getFirst().capabilityKey());
        assertEquals(6L, response.getFirst().deviceCount());
        assertEquals(now, response.getFirst().latestCaptureTime());
    }
}
