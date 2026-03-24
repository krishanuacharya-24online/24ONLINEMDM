package com.e24online.mdm.service;

import com.e24online.mdm.enums.StatusViewKind;
import com.e24online.mdm.records.remediation.StatusView;
import com.e24online.mdm.records.reports.RemediationFleetSummaryResponse;
import com.e24online.mdm.records.ui.DataTableResponse;
import com.e24online.mdm.records.user.UserPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static com.e24online.mdm.utils.WorkflowStatusModel.REMEDIATION_STATUSES;
import static com.e24online.mdm.utils.WorkflowStatusModel.canonicalRemediationStatus;

@Service
public class RemediationReportingService {

    private static final Set<String> RESOLVED_REMEDIATION_STATUSES = Set.of("RESOLVED_ON_RESCAN", "CLOSED");
    private static final String OPEN_STATUS_SQL = sqlStringList(com.e24online.mdm.utils.WorkflowStatusModel.OPEN_REMEDIATION_STATUSES);
    private static final String RESOLVED_STATUS_SQL = sqlStringList(RESOLVED_REMEDIATION_STATUSES);
    private static final String REMEDIATION_SNAPSHOT_CTE = """
            WITH remediation_issue_snapshot AS (
                SELECT
                    r.id AS remediation_id,
                    COALESCE(p.tenant_id, '') AS tenant_id,
                    p.device_external_id,
                    run.id AS posture_evaluation_run_id,
                    run.device_trust_profile_id,
                    run.decision_action,
                    run.evaluated_at,
                    r.remediation_rule_id,
                    rr.remediation_code,
                    rr.title AS remediation_title,
                    rr.remediation_type,
                    UPPER(COALESCE(r.source_type, '')) AS source_type,
                    UPPER(COALESCE(m.match_source, '')) AS match_source,
                    UPPER(COALESCE(r.remediation_status, '')) AS remediation_status,
                    r.created_at AS opened_at,
                    r.completed_at AS verified_at,
                    COALESCE(r.completed_at, r.created_at) AS status_updated_at,
                    ROW_NUMBER() OVER (
                        PARTITION BY COALESCE(p.tenant_id, ''),
                                     p.device_external_id,
                                     r.remediation_rule_id,
                                     UPPER(COALESCE(r.source_type, '')),
                                     UPPER(COALESCE(m.match_source, '')),
                                     COALESCE(m.system_information_rule_id, -1),
                                     COALESCE(m.reject_application_list_id, -1),
                                     COALESCE(m.trust_score_policy_id, -1),
                                     COALESCE(m.os_release_lifecycle_master_id, -1)
                        ORDER BY COALESCE(r.completed_at, r.created_at) DESC, r.id DESC
                    ) AS rn
                FROM posture_evaluation_remediation r
                JOIN posture_evaluation_run run
                  ON run.id = r.posture_evaluation_run_id
                JOIN device_posture_payload p
                  ON p.id = run.device_posture_payload_id
                LEFT JOIN remediation_rule rr
                  ON rr.id = r.remediation_rule_id
                LEFT JOIN posture_evaluation_match m
                  ON m.id = r.posture_evaluation_match_id
                WHERE (:tenantFilterDisabled = TRUE OR LOWER(COALESCE(p.tenant_id, '')) = :tenantId)
            )
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final TenantEntitlementService tenantEntitlementService;
    private final BlockingDb blockingDb;

    public RemediationReportingService(NamedParameterJdbcTemplate jdbc,
                                       TenantEntitlementService tenantEntitlementService,
                                       BlockingDb blockingDb) {
        this.jdbc = jdbc;
        this.tenantEntitlementService = tenantEntitlementService;
        this.blockingDb = blockingDb;
    }

    public Mono<Void> assertCanAccessReports(UserPrincipal principal, String tenantId) {
        return blockingDb.mono(() -> {
            assertCanAccessReportsBlocking(principal, tenantId);
            return Boolean.TRUE;
        }).then();
    }

    public Mono<RemediationFleetSummaryResponse> getRemediationSummary(UserPrincipal principal, String tenantId) {
        return blockingDb.mono(() -> {
            assertCanAccessReportsBlocking(principal, tenantId);
            return getRemediationSummaryBlocking(tenantId);
        });
    }

    public Mono<DataTableResponse<Map<String, Object>>> getRemediationTable(UserPrincipal principal,
                                                                            String tenantId,
                                                                            int draw,
                                                                            int start,
                                                                            int length,
                                                                            String statusView,
                                                                            String search,
                                                                            String sortBy,
                                                                            String sortDir) {
        return blockingDb.mono(() -> {
            assertCanAccessReportsBlocking(principal, tenantId);
            return getRemediationTableBlocking(draw, start, length, tenantId, statusView, search, sortBy, sortDir);
        });
    }

    private RemediationFleetSummaryResponse getRemediationSummaryBlocking(String tenantId) {
        MapSqlParameterSource params = params(tenantId);
        String summarySql = (REMEDIATION_SNAPSHOT_CTE + """
                SELECT
                    COUNT(*) AS total_tracked_issues,
                    SUM(CASE WHEN remediation_status IN (%s) THEN 1 ELSE 0 END) AS open_issues,
                    SUM(CASE WHEN remediation_status IN (%s) THEN 1 ELSE 0 END) AS resolved_issues,
                    SUM(CASE WHEN remediation_status = 'USER_ACKNOWLEDGED' THEN 1 ELSE 0 END) AS awaiting_verification_issues,
                    SUM(CASE WHEN remediation_status = 'STILL_OPEN' THEN 1 ELSE 0 END) AS still_open_issues,
                    SUM(CASE WHEN remediation_status = 'RESOLVED_ON_RESCAN' THEN 1 ELSE 0 END) AS resolved_on_rescan_issues,
                    COUNT(DISTINCT CASE WHEN remediation_status IN (%s) THEN device_external_id END) AS devices_with_open_issues,
                    MAX(CASE WHEN remediation_status = 'RESOLVED_ON_RESCAN' THEN verified_at END) AS latest_resolved_at
                FROM remediation_issue_snapshot
                WHERE rn = 1
                """).formatted(OPEN_STATUS_SQL, RESOLVED_STATUS_SQL, OPEN_STATUS_SQL);
        Map<String, Object> row = jdbc.queryForMap(summarySql, params);

        return new RemediationFleetSummaryResponse(
                tenantId,
                longValue(row.get("total_tracked_issues")),
                longValue(row.get("open_issues")),
                longValue(row.get("resolved_issues")),
                longValue(row.get("devices_with_open_issues")),
                longValue(row.get("awaiting_verification_issues")),
                longValue(row.get("still_open_issues")),
                longValue(row.get("resolved_on_rescan_issues")),
                offsetDateTimeValue(row.get("latest_resolved_at"))
        );
    }

    private DataTableResponse<Map<String, Object>> getRemediationTableBlocking(int draw,
                                                                               int start,
                                                                               int length,
                                                                               String tenantId,
                                                                               String statusView,
                                                                               String search,
                                                                               String sortBy,
                                                                               String sortDir) {
        MapSqlParameterSource params = params(tenantId);
        String statusPredicate = statusPredicate(normalizeStatusView(statusView), params);
        String searchPredicate = searchPredicate(search, params);
        String orderBy = resolveOrderColumn(sortBy);
        String orderDir = resolveSortDirection(sortDir);
        int safeLength = clampLength(length);
        int safeStart = Math.max(start, 0);

        Long total = jdbc.queryForObject(
                REMEDIATION_SNAPSHOT_CTE + """
                        SELECT COUNT(*)
                        FROM remediation_issue_snapshot
                        WHERE rn = 1
                        """ + statusPredicate,
                params,
                Long.class
        );
        long recordsTotal = total == null ? 0L : total;

        long recordsFiltered = recordsTotal;
        if (!searchPredicate.isBlank()) {
            Long filtered = jdbc.queryForObject(
                    REMEDIATION_SNAPSHOT_CTE + """
                            SELECT COUNT(*)
                            FROM remediation_issue_snapshot
                            WHERE rn = 1
                            """ + statusPredicate + searchPredicate,
                    params,
                    Long.class
            );
            recordsFiltered = filtered == null ? 0L : filtered;
        }

        params.addValue("limit", safeLength);
        params.addValue("offset", safeStart);

        List<Map<String, Object>> data = jdbc.queryForList(
                REMEDIATION_SNAPSHOT_CTE + """
                        SELECT
                            remediation_id AS id,
                            tenant_id,
                            device_external_id,
                            posture_evaluation_run_id,
                            remediation_rule_id,
                            remediation_code,
                            remediation_title,
                            remediation_type,
                            remediation_status,
                            source_type,
                            match_source,
                            decision_action,
                            opened_at,
                            verified_at,
                            status_updated_at
                        FROM remediation_issue_snapshot
                        WHERE rn = 1
                        """ + statusPredicate + searchPredicate + """
                        ORDER BY
                        """ + orderBy + " " + orderDir + ", id DESC " + """
                        LIMIT :limit OFFSET :offset
                        """,
                params
        );

        return new DataTableResponse<>(draw, recordsTotal, recordsFiltered, data);
    }

    private void assertCanAccessReportsBlocking(UserPrincipal principal, String tenantId) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }

        String role = principal.role() == null ? "" : principal.role().trim().toUpperCase(Locale.ROOT);
        if ("PRODUCT_ADMIN".equals(role)) {
            return;
        }
        if (!"TENANT_ADMIN".equals(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Unsupported role");
        }
        tenantEntitlementService.assertCanAccessPremiumReporting(tenantId);
    }

    private String statusPredicate(StatusView statusView, MapSqlParameterSource params) {
        return switch (statusView.kind()) {
            case ALL -> "";
            case OPEN -> " AND remediation_status IN (" + OPEN_STATUS_SQL + ")";
            case RESOLVED -> " AND remediation_status IN (" + RESOLVED_STATUS_SQL + ")";
            case EXACT -> {
                params.addValue("statusView", statusView.value());
                yield " AND remediation_status = :statusView";
            }
        };
    }

    private String searchPredicate(String search, MapSqlParameterSource params) {
        String term = normalizeOptionalText(search);
        if (term == null) {
            return "";
        }
        params.addValue("searchTerm", "%" + term.toLowerCase(Locale.ROOT) + "%");
        return """
                 AND (
                    LOWER(CAST(tenant_id AS TEXT)) LIKE :searchTerm
                    OR LOWER(CAST(device_external_id AS TEXT)) LIKE :searchTerm
                    OR LOWER(CAST(remediation_code AS TEXT)) LIKE :searchTerm
                    OR LOWER(CAST(remediation_title AS TEXT)) LIKE :searchTerm
                    OR LOWER(CAST(remediation_status AS TEXT)) LIKE :searchTerm
                    OR LOWER(CAST(source_type AS TEXT)) LIKE :searchTerm
                    OR LOWER(CAST(match_source AS TEXT)) LIKE :searchTerm
                    OR LOWER(CAST(decision_action AS TEXT)) LIKE :searchTerm
                 )
                """;
    }

    private String resolveOrderColumn(String sortBy) {
        Map<String, String> sortableColumns = new LinkedHashMap<>();
        sortableColumns.put("id", "id");
        sortableColumns.put("tenant_id", "tenant_id");
        sortableColumns.put("device_external_id", "device_external_id");
        sortableColumns.put("remediation_code", "remediation_code");
        sortableColumns.put("remediation_title", "remediation_title");
        sortableColumns.put("remediation_status", "remediation_status");
        sortableColumns.put("decision_action", "decision_action");
        sortableColumns.put("opened_at", "opened_at");
        sortableColumns.put("verified_at", "verified_at");
        sortableColumns.put("status_updated_at", "status_updated_at");
        String normalized = normalizeOptionalText(sortBy);
        if (normalized == null) {
            return "status_updated_at";
        }
        return sortableColumns.getOrDefault(normalized, "status_updated_at");
    }

    private String resolveSortDirection(String sortDir) {
        String normalized = normalizeOptionalText(sortDir);
        return "ASC".equalsIgnoreCase(normalized) ? "ASC" : "DESC";
    }

    private StatusView normalizeStatusView(String statusView) {
        String normalized = normalizeOptionalText(statusView);
        if (normalized == null || "ALL".equalsIgnoreCase(normalized)) {
            return new StatusView(StatusViewKind.ALL, null);
        }
        String upper = normalized.toUpperCase(Locale.ROOT);
        if ("OPEN".equals(upper)) {
            return new StatusView(StatusViewKind.OPEN, null);
        }
        if ("RESOLVED".equals(upper)) {
            return new StatusView(StatusViewKind.RESOLVED, null);
        }
        String canonical = canonicalRemediationStatus(upper);
        if (canonical != null && REMEDIATION_STATUSES.contains(canonical)) {
            return new StatusView(StatusViewKind.EXACT, canonical);
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status_view is invalid");
    }

    private MapSqlParameterSource params(String tenantId) {
        String normalizedTenantId = normalizeOptionalText(tenantId);
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("tenantId", normalizedTenantId == null ? null : normalizedTenantId.toLowerCase(Locale.ROOT));
        params.addValue("tenantFilterDisabled", normalizedTenantId == null);
        return params;
    }

    private String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private int clampLength(int length) {
        if (length <= 0) {
            return 25;
        }
        return Math.min(length, 200);
    }

    private long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Long.parseLong(text);
        }
        return 0L;
    }

    private OffsetDateTime offsetDateTimeValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant().atOffset(ZoneOffset.UTC);
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime.atOffset(ZoneOffset.UTC);
        }
        if (value instanceof Instant instant) {
            return instant.atOffset(ZoneOffset.UTC);
        }
        if (value instanceof String text && !text.isBlank()) {
            return OffsetDateTime.parse(text);
        }
        return null;
    }

    private static String sqlStringList(Set<String> values) {
        return values.stream()
                .sorted()
                .map(value -> "'" + value + "'")
                .reduce((left, right) -> left + ", " + right)
                .orElse("''");
    }

}
