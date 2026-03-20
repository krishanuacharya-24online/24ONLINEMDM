package com.e24online.mdm.service;

import com.e24online.mdm.records.reports.RemediationFleetSummaryResponse;
import com.e24online.mdm.records.ui.DataTableResponse;
import com.e24online.mdm.records.user.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.scheduler.Schedulers;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RemediationReportingServiceTest {

    @Mock
    private NamedParameterJdbcTemplate jdbc;

    @Mock
    private TenantEntitlementService tenantEntitlementService;

    private RemediationReportingService service;

    @BeforeEach
    void setUp() {
        service = new RemediationReportingService(
                jdbc,
                tenantEntitlementService,
                new BlockingDb(Schedulers.immediate())
        );
    }

    @Test
    void getRemediationSummary_mapsAggregateCounts() {
        OffsetDateTime resolvedAt = OffsetDateTime.now();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("total_tracked_issues", 12L);
        row.put("open_issues", 7L);
        row.put("resolved_issues", 5L);
        row.put("devices_with_open_issues", 4L);
        row.put("awaiting_verification_issues", 2L);
        row.put("still_open_issues", 3L);
        row.put("resolved_on_rescan_issues", 5L);
        row.put("latest_resolved_at", resolvedAt);

        when(jdbc.queryForMap(anyString(), any(MapSqlParameterSource.class))).thenReturn(row);

        RemediationFleetSummaryResponse response = service.getRemediationSummary(
                new UserPrincipal(1L, "admin", "PRODUCT_ADMIN", null),
                null
        ).block();

        assertNotNull(response);
        assertEquals(12L, response.totalTrackedIssues());
        assertEquals(7L, response.openIssues());
        assertEquals(5L, response.resolvedIssues());
        assertEquals(4L, response.devicesWithOpenIssues());
        assertEquals(2L, response.awaitingVerificationIssues());
        assertEquals(3L, response.stillOpenIssues());
        assertEquals(5L, response.resolvedOnRescanIssues());
        assertEquals(resolvedAt, response.latestResolvedAt());
        verify(tenantEntitlementService, never()).assertCanAccessPremiumReporting(any());
    }

    @Test
    void getRemediationSummary_avoidsUntypedNullTenantPredicate() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("total_tracked_issues", 0L);
        row.put("open_issues", 0L);
        row.put("resolved_issues", 0L);
        row.put("devices_with_open_issues", 0L);
        row.put("awaiting_verification_issues", 0L);
        row.put("still_open_issues", 0L);
        row.put("resolved_on_rescan_issues", 0L);
        row.put("latest_resolved_at", null);
        when(jdbc.queryForMap(anyString(), any(MapSqlParameterSource.class))).thenReturn(row);

        service.getRemediationSummary(
                new UserPrincipal(1L, "admin", "PRODUCT_ADMIN", null),
                "acme-fix"
        ).block();

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).queryForMap(sqlCaptor.capture(), paramsCaptor.capture());

        String sql = sqlCaptor.getValue();
        MapSqlParameterSource params = paramsCaptor.getValue();

        assertFalse(sql.contains(":tenantId IS NULL"));
        assertTrue(sql.contains(":tenantFilterDisabled = TRUE"));
        assertEquals("acme-fix", params.getValue("tenantId"));
        assertEquals(false, params.getValue("tenantFilterDisabled"));
    }

    @Test
    void getRemediationTable_requiresPremiumReportingForTenantAdmin() {
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Current tenant subscription does not include premium reporting"))
                .when(tenantEntitlementService)
                .assertCanAccessPremiumReporting("tenant-a");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                service.getRemediationTable(
                        new UserPrincipal(2L, "tenant-admin", "TENANT_ADMIN", 8L),
                        "tenant-a",
                        1,
                        0,
                        25,
                        "OPEN",
                        null,
                        "status_updated_at",
                        "desc"
                ).block());

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void getRemediationTable_returnsPagedLatestIssueRows() {
        when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class)))
                .thenReturn(2L, 1L);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", 501L);
        row.put("tenant_id", "tenant-a");
        row.put("device_external_id", "dev-1");
        row.put("remediation_code", "R-1");
        row.put("remediation_title", "Update OS");
        row.put("remediation_status", "STILL_OPEN");
        row.put("source_type", "MATCH");
        row.put("match_source", "SYSTEM_RULE");
        row.put("decision_action", "BLOCK");
        row.put("opened_at", OffsetDateTime.now().minusDays(1));
        row.put("verified_at", null);
        row.put("status_updated_at", OffsetDateTime.now());
        when(jdbc.queryForList(anyString(), any(MapSqlParameterSource.class))).thenReturn(List.of(row));

        DataTableResponse<Map<String, Object>> response = service.getRemediationTable(
                new UserPrincipal(1L, "admin", "PRODUCT_ADMIN", null),
                null,
                3,
                0,
                25,
                "OPEN",
                "dev",
                "status_updated_at",
                "desc"
        ).block();

        assertNotNull(response);
        assertEquals(3, response.draw());
        assertEquals(2L, response.recordsTotal());
        assertEquals(1L, response.recordsFiltered());
        assertEquals(1, response.data().size());
        assertEquals("tenant-a", response.data().getFirst().get("tenant_id"));
        verify(tenantEntitlementService, never()).assertCanAccessPremiumReporting(any());

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForList(sqlCaptor.capture(), any(MapSqlParameterSource.class));
        assertTrue(sqlCaptor.getValue().contains("ORDER BY"));
        assertTrue(sqlCaptor.getValue().contains("status_updated_at DESC, id DESC"));
    }
}
