package com.e24online.mdm.service;

import com.e24online.mdm.records.DataTablePage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UiDataTableServiceTest {

    @Mock
    private NamedParameterJdbcTemplate jdbc;

    private UiDataTableService service;

    @BeforeEach
    void setUp() {
        service = new UiDataTableService(jdbc);
    }

    @Test
    void query_returnsSeparateTotalAndFilteredCounts() {
        when(jdbc.queryForObject(contains("information_schema.columns"), anyMap(), eq(Boolean.class)))
                .thenReturn(Boolean.TRUE);
        when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class)))
                .thenAnswer(invocation -> {
                    String sql = invocation.getArgument(0, String.class);
                    return sql.contains(" LIKE :searchTerm") ? 3L : 12L;
                });
        when(jdbc.queryForList(anyString(), any(MapSqlParameterSource.class)))
                .thenReturn(List.of(Map.of("id", 1L, "rule_code", "RULE_1")));

        DataTablePage page = service.systemRules(
                1,
                0,
                25,
                null,
                "ACTIVE",
                "android",
                "id",
                "DESC"
        );

        assertEquals(12L, page.recordsTotal());
        assertEquals(3L, page.recordsFiltered());
        assertEquals(1, page.data().size());
    }

    @Test
    void policyAudit_query_returnsRowsForTenantWithGlobalFallback() {
        when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class)))
                .thenReturn(5L);
        when(jdbc.queryForList(anyString(), any(MapSqlParameterSource.class)))
                .thenReturn(List.of(Map.of("id", 9L, "policy_type", "SYSTEM_RULE", "operation", "CREATE")));

        DataTablePage page = service.policyAudit(
                2,
                0,
                25,
                "tenant-a",
                "SYSTEM_RULE",
                "CREATE",
                "admin",
                null,
                "created_at",
                "DESC"
        );

        assertEquals(5L, page.recordsTotal());
        assertEquals(5L, page.recordsFiltered());
        assertEquals(1, page.data().size());
    }

    @Test
    void policyAudit_query_allowsProductAdminToSeeAllTenantsWhenTenantFilterMissing() {
        when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class)))
                .thenReturn(7L);
        when(jdbc.queryForList(anyString(), any(MapSqlParameterSource.class)))
                .thenReturn(List.of(Map.of("id", 10L, "policy_type", "SYSTEM_RULE", "operation", "UPDATE")));

        DataTablePage page = service.policyAudit(
                3,
                0,
                25,
                null,
                null,
                null,
                null,
                null,
                "created_at",
                "DESC"
        );

        assertEquals(7L, page.recordsTotal());
        assertEquals(7L, page.recordsFiltered());
        assertEquals(1, page.data().size());

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbc, atLeastOnce()).queryForObject(sqlCaptor.capture(), any(MapSqlParameterSource.class), eq(Long.class));
        boolean foundRbacPredicate = sqlCaptor.getAllValues().stream()
                .anyMatch(sql -> sql.contains("CAST(:tenantId AS TEXT) IS NULL")
                        && sql.contains("OR pca.tenant_id IS NULL")
                        && sql.contains("OR LOWER(pca.tenant_id) = LOWER(CAST(:tenantId AS TEXT))"));
        assertEquals(true, foundRbacPredicate);
    }

    @Test
    void auditEvents_query_returnsRowsAcrossCategories() {
        when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class)))
                .thenReturn(4L);
        when(jdbc.queryForList(anyString(), any(MapSqlParameterSource.class)))
                .thenReturn(List.of(Map.of(
                        "id", 11L,
                        "event_category", "AUTH",
                        "event_type", "USER_LOGIN",
                        "status", "SUCCESS"
                )));

        DataTablePage page = service.auditEvents(
                3,
                0,
                25,
                null,
                "AUTH",
                "USER_LOGIN",
                "LOGIN",
                "SUCCESS",
                "admin",
                null,
                "created_at",
                "DESC"
        );

        assertEquals(4L, page.recordsTotal());
        assertEquals(4L, page.recordsFiltered());
        assertEquals(1, page.data().size());
    }
}
