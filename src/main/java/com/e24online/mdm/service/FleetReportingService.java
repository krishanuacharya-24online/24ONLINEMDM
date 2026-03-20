package com.e24online.mdm.service;

import com.e24online.mdm.records.reports.AgentCapabilityCoverageResponse;
import com.e24online.mdm.records.reports.AgentVersionDistributionResponse;
import com.e24online.mdm.records.reports.FleetOperationalSummaryResponse;
import com.e24online.mdm.records.reports.ScoreTrendPointResponse;
import com.e24online.mdm.records.reports.TopFailingRuleResponse;
import com.e24online.mdm.records.reports.TopRiskyApplicationResponse;
import com.e24online.mdm.records.ui.DataTableResponse;
import com.e24online.mdm.records.user.UserPrincipal;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class FleetReportingService {

    private static final int DEFAULT_STALE_AFTER_HOURS = 72;
    private static final int MAX_STALE_AFTER_HOURS = 24 * 365;
    private static final int DEFAULT_TREND_DAYS = 14;
    private static final int MAX_TREND_DAYS = 90;
    private static final int DEFAULT_TOP_LIMIT = 8;
    private static final int MAX_TOP_LIMIT = 25;
    private static final String DEVICE_STATE_CTE = """
            WITH fleet_device_state AS (
                SELECT
                    dtp.id AS device_trust_profile_id,
                    COALESCE(dtp.tenant_id, '') AS tenant_id,
                    dtp.device_external_id,
                    dtp.os_type,
                    dtp.os_name,
                    dtp.current_score,
                    dtp.score_band,
                    dtp.posture_status,
                    dtp.os_lifecycle_state,
                    COALESCE(dtp.last_event_at, dtp.last_recalculated_at, dtp.modified_at, dtp.created_at) AS latest_seen_at
                FROM device_trust_profile dtp
                WHERE dtp.is_deleted = false
                  AND (:tenantFilterDisabled = TRUE OR LOWER(COALESCE(dtp.tenant_id, '')) = :tenantId)
            )
            """;
    private static final String LATEST_PAYLOAD_CTE = """
            WITH latest_device_payload AS (
                SELECT
                    payload.id AS device_posture_payload_id,
                    COALESCE(payload.tenant_id, '') AS tenant_id,
                    payload.device_external_id,
                    COALESCE(payload.tenant_id, '') || ':' || COALESCE(payload.device_external_id, '') AS device_key,
                    payload.agent_version,
                    payload.schema_compatibility_status,
                    payload.agent_capabilities,
                    COALESCE(payload.capture_time, payload.received_at, payload.created_at) AS latest_capture_time,
                    ROW_NUMBER() OVER (
                        PARTITION BY COALESCE(payload.tenant_id, ''), payload.device_external_id
                        ORDER BY COALESCE(payload.capture_time, payload.received_at, payload.created_at) DESC, payload.id DESC
                    ) AS rn
                FROM device_posture_payload payload
                WHERE (:tenantFilterDisabled = TRUE OR LOWER(COALESCE(payload.tenant_id, '')) = :tenantId)
            )
            """;
    private static final String LATEST_RUN_CTE = """
            WITH latest_fleet_run AS (
                SELECT
                    run.id AS posture_evaluation_run_id,
                    run.device_trust_profile_id,
                    run.decision_action,
                    run.evaluated_at,
                    COALESCE(dtp.tenant_id, '') AS tenant_id,
                    dtp.device_external_id,
                    ROW_NUMBER() OVER (
                        PARTITION BY run.device_trust_profile_id
                        ORDER BY run.evaluated_at DESC, run.id DESC
                    ) AS rn
                FROM posture_evaluation_run run
                JOIN device_trust_profile dtp
                  ON dtp.id = run.device_trust_profile_id
                WHERE dtp.is_deleted = false
                  AND (:tenantFilterDisabled = TRUE OR LOWER(COALESCE(dtp.tenant_id, '')) = :tenantId)
            )
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final RemediationReportingService remediationReportingService;
    private final BlockingDb blockingDb;

    public FleetReportingService(NamedParameterJdbcTemplate jdbc,
                                 RemediationReportingService remediationReportingService,
                                 BlockingDb blockingDb) {
        this.jdbc = jdbc;
        this.remediationReportingService = remediationReportingService;
        this.blockingDb = blockingDb;
    }

    public Mono<FleetOperationalSummaryResponse> getFleetSummary(UserPrincipal principal,
                                                                 String tenantId,
                                                                 Integer staleAfterHours) {
        int normalizedStaleAfterHours = normalizeStaleAfterHours(staleAfterHours);
        return remediationReportingService.assertCanAccessReports(principal, tenantId)
                .then(blockingDb.mono(() -> getFleetSummaryBlocking(tenantId, normalizedStaleAfterHours)));
    }

    public Mono<DataTableResponse<Map<String, Object>>> getStaleDevicesTable(UserPrincipal principal,
                                                                             String tenantId,
                                                                             int draw,
                                                                             int start,
                                                                             int length,
                                                                             Integer staleAfterHours,
                                                                             String search,
                                                                             String sortBy,
                                                                             String sortDir) {
        int normalizedStaleAfterHours = normalizeStaleAfterHours(staleAfterHours);
        return remediationReportingService.assertCanAccessReports(principal, tenantId)
                .then(blockingDb.mono(() -> getStaleDevicesTableBlocking(
                        draw,
                        start,
                        length,
                        tenantId,
                        normalizedStaleAfterHours,
                        search,
                        sortBy,
                        sortDir
                )));
    }

    public Mono<List<TopFailingRuleResponse>> getTopFailingRules(UserPrincipal principal,
                                                                 String tenantId,
                                                                 Integer limit) {
        int normalizedLimit = normalizeTopLimit(limit);
        return remediationReportingService.assertCanAccessReports(principal, tenantId)
                .then(blockingDb.mono(() -> getTopFailingRulesBlocking(tenantId, normalizedLimit)));
    }

    public Mono<List<TopRiskyApplicationResponse>> getTopRiskyApplications(UserPrincipal principal,
                                                                            String tenantId,
                                                                            Integer limit) {
        int normalizedLimit = normalizeTopLimit(limit);
        return remediationReportingService.assertCanAccessReports(principal, tenantId)
                .then(blockingDb.mono(() -> getTopRiskyApplicationsBlocking(tenantId, normalizedLimit)));
    }

    public Mono<List<ScoreTrendPointResponse>> getScoreTrend(UserPrincipal principal,
                                                             String tenantId,
                                                             Integer days) {
        int normalizedDays = normalizeTrendDays(days);
        return remediationReportingService.assertCanAccessReports(principal, tenantId)
                .then(blockingDb.mono(() -> getScoreTrendBlocking(tenantId, normalizedDays)));
    }

    public Mono<List<AgentVersionDistributionResponse>> getAgentVersionDistribution(UserPrincipal principal,
                                                                                    String tenantId,
                                                                                    Integer limit) {
        int normalizedLimit = normalizeTopLimit(limit);
        return remediationReportingService.assertCanAccessReports(principal, tenantId)
                .then(blockingDb.mono(() -> getAgentVersionDistributionBlocking(tenantId, normalizedLimit)));
    }

    public Mono<List<AgentCapabilityCoverageResponse>> getAgentCapabilityCoverage(UserPrincipal principal,
                                                                                  String tenantId,
                                                                                  Integer limit) {
        int normalizedLimit = normalizeTopLimit(limit);
        return remediationReportingService.assertCanAccessReports(principal, tenantId)
                .then(blockingDb.mono(() -> getAgentCapabilityCoverageBlocking(tenantId, normalizedLimit)));
    }

    private FleetOperationalSummaryResponse getFleetSummaryBlocking(String tenantId, int staleAfterHours) {
        Map<String, Object> row = jdbc.queryForMap(
                DEVICE_STATE_CTE + """
                        SELECT
                            COUNT(*) AS total_devices,
                            SUM(CASE WHEN latest_seen_at < :staleBefore THEN 1 ELSE 0 END) AS stale_devices,
                            SUM(CASE WHEN score_band IN ('HIGH_RISK', 'CRITICAL') THEN 1 ELSE 0 END) AS high_risk_devices,
                            SUM(CASE WHEN score_band = 'CRITICAL' THEN 1 ELSE 0 END) AS critical_devices,
                            SUM(CASE WHEN os_lifecycle_state IN ('EOL', 'EEOL') THEN 1 ELSE 0 END) AS lifecycle_risk_devices,
                            SUM(CASE WHEN os_lifecycle_state = 'SUPPORTED' THEN 1 ELSE 0 END) AS supported_devices,
                            SUM(CASE WHEN os_lifecycle_state = 'EOL' THEN 1 ELSE 0 END) AS eol_devices,
                            SUM(CASE WHEN os_lifecycle_state = 'EEOL' THEN 1 ELSE 0 END) AS eeol_devices,
                            SUM(CASE WHEN os_lifecycle_state = 'NOT_TRACKED' THEN 1 ELSE 0 END) AS not_tracked_devices
                        FROM fleet_device_state
                        """,
                params(tenantId, staleAfterHours)
        );

        return new FleetOperationalSummaryResponse(
                tenantId,
                staleAfterHours,
                longValue(row.get("total_devices")),
                longValue(row.get("stale_devices")),
                longValue(row.get("high_risk_devices")),
                longValue(row.get("critical_devices")),
                longValue(row.get("lifecycle_risk_devices")),
                longValue(row.get("supported_devices")),
                longValue(row.get("eol_devices")),
                longValue(row.get("eeol_devices")),
                longValue(row.get("not_tracked_devices"))
        );
    }

    private DataTableResponse<Map<String, Object>> getStaleDevicesTableBlocking(int draw,
                                                                                int start,
                                                                                int length,
                                                                                String tenantId,
                                                                                int staleAfterHours,
                                                                                String search,
                                                                                String sortBy,
                                                                                String sortDir) {
        MapSqlParameterSource params = params(tenantId, staleAfterHours);
        String searchPredicate = staleDeviceSearchPredicate(search, params);
        String orderBy = resolveStaleOrderColumn(sortBy);
        String orderDir = resolveSortDirection(sortDir);
        int safeLength = clampLength(length);
        int safeStart = Math.max(start, 0);

        Long total = jdbc.queryForObject(
                DEVICE_STATE_CTE + """
                        SELECT COUNT(*)
                        FROM fleet_device_state
                        WHERE latest_seen_at < :staleBefore
                        """,
                params,
                Long.class
        );
        long recordsTotal = total == null ? 0L : total;

        long recordsFiltered = recordsTotal;
        if (!searchPredicate.isBlank()) {
            Long filtered = jdbc.queryForObject(
                    DEVICE_STATE_CTE + """
                            SELECT COUNT(*)
                            FROM fleet_device_state
                            WHERE latest_seen_at < :staleBefore
                            """ + searchPredicate,
                    params,
                    Long.class
            );
            recordsFiltered = filtered == null ? 0L : filtered;
        }

        params.addValue("limit", safeLength);
        params.addValue("offset", safeStart);

        List<Map<String, Object>> data = jdbc.queryForList(
                DEVICE_STATE_CTE + """
                        SELECT
                            device_trust_profile_id AS id,
                            tenant_id,
                            device_external_id,
                            os_type,
                            os_name,
                            current_score,
                            score_band,
                            posture_status,
                            os_lifecycle_state,
                            latest_seen_at
                        FROM fleet_device_state
                        WHERE latest_seen_at < :staleBefore
                        """ + searchPredicate + """
                        ORDER BY
                        """ + orderBy + " " + orderDir + ", id DESC " + """
                        LIMIT :limit OFFSET :offset
                        """,
                params
        );

        return new DataTableResponse<>(draw, recordsTotal, recordsFiltered, data);
    }

    private List<TopFailingRuleResponse> getTopFailingRulesBlocking(String tenantId, int limit) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                LATEST_RUN_CTE + """
                        SELECT
                            COALESCE(sir.id, m.system_information_rule_id) AS rule_id,
                            COALESCE(sir.rule_code, 'SYSTEM_RULE#' || CAST(COALESCE(m.system_information_rule_id, 0) AS TEXT)) AS rule_code,
                            COALESCE(NULLIF(sir.rule_tag, ''), 'UNCLASSIFIED') AS rule_tag,
                            COALESCE(sir.description, '') AS rule_description,
                            COALESCE(sir.compliance_action, m.compliance_action, 'ALLOW') AS compliance_action,
                            COUNT(*) AS current_match_count,
                            COUNT(DISTINCT lr.device_trust_profile_id) AS impacted_devices,
                            SUM(CASE WHEN lr.decision_action = 'BLOCK' THEN 1 ELSE 0 END) AS blocked_devices,
                            MAX(lr.evaluated_at) AS latest_evaluated_at
                        FROM latest_fleet_run lr
                        JOIN posture_evaluation_match m
                          ON m.posture_evaluation_run_id = lr.posture_evaluation_run_id
                        LEFT JOIN system_information_rule sir
                          ON sir.id = m.system_information_rule_id
                        WHERE lr.rn = 1
                          AND m.match_source = 'SYSTEM_RULE'
                        GROUP BY
                            COALESCE(sir.id, m.system_information_rule_id),
                            COALESCE(sir.rule_code, 'SYSTEM_RULE#' || CAST(COALESCE(m.system_information_rule_id, 0) AS TEXT)),
                            COALESCE(NULLIF(sir.rule_tag, ''), 'UNCLASSIFIED'),
                            COALESCE(sir.description, ''),
                            COALESCE(sir.compliance_action, m.compliance_action, 'ALLOW')
                        ORDER BY impacted_devices DESC, current_match_count DESC, rule_code ASC
                        LIMIT :limit
                        """,
                params(tenantId, null).addValue("limit", limit)
        );

        return rows.stream()
                .map(row -> new TopFailingRuleResponse(
                        longObjectValue(row.get("rule_id")),
                        stringValue(row.get("rule_code")),
                        stringValue(row.get("rule_tag")),
                        stringValue(row.get("rule_description")),
                        stringValue(row.get("compliance_action")),
                        longValue(row.get("impacted_devices")),
                        longValue(row.get("blocked_devices")),
                        longValue(row.get("current_match_count")),
                        offsetDateTimeValue(row.get("latest_evaluated_at"))
                ))
                .toList();
    }

    private List<TopRiskyApplicationResponse> getTopRiskyApplicationsBlocking(String tenantId, int limit) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                LATEST_RUN_CTE + """
                        , current_risky_app_matches AS (
                            SELECT
                                lr.device_trust_profile_id,
                                lr.decision_action,
                                lr.evaluated_at,
                                COALESCE(NULLIF(dia.app_name, ''), NULLIF(ral.app_name, ''), 'Unknown application') AS app_name,
                                COALESCE(NULLIF(dia.package_id, ''), NULLIF(ral.package_id, '')) AS package_id,
                                COALESCE(NULLIF(dia.publisher, ''), NULLIF(ral.publisher, ''), 'Unknown publisher') AS publisher,
                                COALESCE(NULLIF(dia.app_os_type, ''), NULLIF(ral.app_os_type, ''), 'UNKNOWN') AS app_os_type,
                                COALESCE(NULLIF(ral.policy_tag, ''), 'REJECT_APP') AS policy_tag,
                                COALESCE(NULLIF(dia.package_id, ''), NULLIF(ral.package_id, ''), LOWER(COALESCE(NULLIF(dia.app_name, ''), NULLIF(ral.app_name, ''), 'unknown-application'))) AS app_key
                            FROM latest_fleet_run lr
                            JOIN posture_evaluation_match m
                              ON m.posture_evaluation_run_id = lr.posture_evaluation_run_id
                            LEFT JOIN device_installed_application dia
                              ON dia.id = m.device_installed_application_id
                            LEFT JOIN reject_application_list ral
                              ON ral.id = m.reject_application_list_id
                            WHERE lr.rn = 1
                              AND m.match_source = 'REJECT_APPLICATION'
                        )
                        SELECT
                            app_name,
                            package_id,
                            publisher,
                            app_os_type,
                            policy_tag,
                            COUNT(*) AS current_match_count,
                            COUNT(DISTINCT device_trust_profile_id) AS impacted_devices,
                            SUM(CASE WHEN decision_action = 'BLOCK' THEN 1 ELSE 0 END) AS blocked_devices,
                            MAX(evaluated_at) AS latest_evaluated_at
                        FROM current_risky_app_matches
                        GROUP BY app_key, app_name, package_id, publisher, app_os_type, policy_tag
                        ORDER BY impacted_devices DESC, current_match_count DESC, blocked_devices DESC, app_name ASC
                        LIMIT :limit
                        """,
                params(tenantId, null).addValue("limit", limit)
        );

        return rows.stream()
                .map(row -> new TopRiskyApplicationResponse(
                        stringValue(row.get("app_name")),
                        stringValue(row.get("package_id")),
                        stringValue(row.get("publisher")),
                        stringValue(row.get("app_os_type")),
                        stringValue(row.get("policy_tag")),
                        longValue(row.get("impacted_devices")),
                        longValue(row.get("blocked_devices")),
                        longValue(row.get("current_match_count")),
                        offsetDateTimeValue(row.get("latest_evaluated_at"))
                ))
                .toList();
    }

    private List<ScoreTrendPointResponse> getScoreTrendBlocking(String tenantId, int days) {
        MapSqlParameterSource params = params(tenantId, null)
                .addValue("trendStart", OffsetDateTime.now(ZoneOffset.UTC).minusDays(days));

        List<Map<String, Object>> rows = jdbc.queryForList(
                """
                        WITH trend_runs AS (
                            SELECT
                                DATE_TRUNC('day', run.evaluated_at AT TIME ZONE 'UTC')::date AS bucket_date,
                                run.device_trust_profile_id,
                                run.decision_action,
                                run.trust_score_after
                            FROM posture_evaluation_run run
                            JOIN device_trust_profile dtp
                              ON dtp.id = run.device_trust_profile_id
                            WHERE dtp.is_deleted = false
                              AND run.evaluation_status = 'COMPLETED'
                              AND run.evaluated_at >= :trendStart
                              AND (:tenantFilterDisabled = TRUE OR LOWER(COALESCE(dtp.tenant_id, '')) = :tenantId)
                        )
                        SELECT
                            bucket_date,
                            COUNT(*) AS evaluation_count,
                            COUNT(DISTINCT device_trust_profile_id) AS distinct_devices,
                            AVG(trust_score_after) AS average_trust_score,
                            SUM(CASE WHEN decision_action = 'ALLOW' THEN 1 ELSE 0 END) AS allow_count,
                            SUM(CASE WHEN decision_action = 'NOTIFY' THEN 1 ELSE 0 END) AS notify_count,
                            SUM(CASE WHEN decision_action = 'QUARANTINE' THEN 1 ELSE 0 END) AS quarantine_count,
                            SUM(CASE WHEN decision_action = 'BLOCK' THEN 1 ELSE 0 END) AS block_count
                        FROM trend_runs
                        GROUP BY bucket_date
                        ORDER BY bucket_date ASC
                        """,
                params
        );

        return rows.stream()
                .map(row -> new ScoreTrendPointResponse(
                        localDateValue(row.get("bucket_date")),
                        longValue(row.get("evaluation_count")),
                        longValue(row.get("distinct_devices")),
                        doubleValue(row.get("average_trust_score")),
                        longValue(row.get("allow_count")),
                        longValue(row.get("notify_count")),
                        longValue(row.get("quarantine_count")),
                        longValue(row.get("block_count"))
                ))
                .toList();
    }

    private List<AgentVersionDistributionResponse> getAgentVersionDistributionBlocking(String tenantId, int limit) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                LATEST_PAYLOAD_CTE + """
                        SELECT
                            COALESCE(NULLIF(agent_version, ''), 'UNKNOWN') AS agent_version,
                            COALESCE(NULLIF(schema_compatibility_status, ''), 'UNVERIFIED') AS schema_compatibility_status,
                            COUNT(*) AS device_count,
                            SUM(
                                CASE
                                    WHEN jsonb_typeof(COALESCE(agent_capabilities, '[]'::jsonb)) = 'array'
                                         AND jsonb_array_length(COALESCE(agent_capabilities, '[]'::jsonb)) > 0
                                    THEN 1
                                    ELSE 0
                                END
                            ) AS devices_with_capabilities,
                            MAX(latest_capture_time) AS latest_capture_time
                        FROM latest_device_payload
                        WHERE rn = 1
                        GROUP BY
                            COALESCE(NULLIF(agent_version, ''), 'UNKNOWN'),
                            COALESCE(NULLIF(schema_compatibility_status, ''), 'UNVERIFIED')
                        ORDER BY device_count DESC, agent_version ASC, schema_compatibility_status ASC
                        LIMIT :limit
                        """,
                params(tenantId, null).addValue("limit", limit)
        );

        return rows.stream()
                .map(row -> new AgentVersionDistributionResponse(
                        stringValue(row.get("agent_version")),
                        stringValue(row.get("schema_compatibility_status")),
                        longValue(row.get("device_count")),
                        longValue(row.get("devices_with_capabilities")),
                        offsetDateTimeValue(row.get("latest_capture_time"))
                ))
                .toList();
    }

    private List<AgentCapabilityCoverageResponse> getAgentCapabilityCoverageBlocking(String tenantId, int limit) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                LATEST_PAYLOAD_CTE + """
                        , capability_rows AS (
                            SELECT
                                payload.device_key,
                                COALESCE(
                                    NULLIF(capability.capability_key, ''),
                                    'UNKNOWN'
                                ) AS capability_key,
                                payload.latest_capture_time
                            FROM latest_device_payload payload
                            CROSS JOIN LATERAL jsonb_array_elements_text(
                                CASE
                                    WHEN jsonb_typeof(COALESCE(payload.agent_capabilities, '[]'::jsonb)) = 'array'
                                    THEN COALESCE(payload.agent_capabilities, '[]'::jsonb)
                                    ELSE '[]'::jsonb
                                END
                            ) AS capability(capability_key)
                            WHERE payload.rn = 1
                        )
                        SELECT
                            capability_key,
                            COUNT(DISTINCT device_key) AS device_count,
                            MAX(latest_capture_time) AS latest_capture_time
                        FROM capability_rows
                        GROUP BY capability_key
                        ORDER BY device_count DESC, capability_key ASC
                        LIMIT :limit
                        """,
                params(tenantId, null).addValue("limit", limit)
        );

        return rows.stream()
                .map(row -> new AgentCapabilityCoverageResponse(
                        stringValue(row.get("capability_key")),
                        longValue(row.get("device_count")),
                        offsetDateTimeValue(row.get("latest_capture_time"))
                ))
                .toList();
    }

    private String staleDeviceSearchPredicate(String search, MapSqlParameterSource params) {
        String term = normalizeOptionalText(search);
        if (term == null) {
            return "";
        }
        params.addValue("searchTerm", "%" + term.toLowerCase(Locale.ROOT) + "%");
        return """
                 AND (
                    LOWER(CAST(tenant_id AS TEXT)) LIKE :searchTerm
                    OR LOWER(CAST(device_external_id AS TEXT)) LIKE :searchTerm
                    OR LOWER(CAST(os_type AS TEXT)) LIKE :searchTerm
                    OR LOWER(CAST(os_name AS TEXT)) LIKE :searchTerm
                    OR LOWER(CAST(score_band AS TEXT)) LIKE :searchTerm
                    OR LOWER(CAST(posture_status AS TEXT)) LIKE :searchTerm
                    OR LOWER(CAST(os_lifecycle_state AS TEXT)) LIKE :searchTerm
                 )
                """;
    }

    private String resolveStaleOrderColumn(String sortBy) {
        Map<String, String> sortableColumns = new LinkedHashMap<>();
        sortableColumns.put("id", "id");
        sortableColumns.put("tenant_id", "tenant_id");
        sortableColumns.put("device_external_id", "device_external_id");
        sortableColumns.put("os_type", "os_type");
        sortableColumns.put("current_score", "current_score");
        sortableColumns.put("score_band", "score_band");
        sortableColumns.put("posture_status", "posture_status");
        sortableColumns.put("os_lifecycle_state", "os_lifecycle_state");
        sortableColumns.put("latest_seen_at", "latest_seen_at");
        String normalized = normalizeOptionalText(sortBy);
        if (normalized == null) {
            return "latest_seen_at";
        }
        return sortableColumns.getOrDefault(normalized, "latest_seen_at");
    }

    private String resolveSortDirection(String sortDir) {
        String normalized = normalizeOptionalText(sortDir);
        return "ASC".equalsIgnoreCase(normalized) ? "ASC" : "DESC";
    }

    private MapSqlParameterSource params(String tenantId, Integer staleAfterHours) {
        String normalizedTenantId = normalizeOptionalText(tenantId);
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("tenantId", normalizedTenantId == null ? null : normalizedTenantId.toLowerCase(Locale.ROOT));
        params.addValue("tenantFilterDisabled", normalizedTenantId == null);
        if (staleAfterHours != null) {
            params.addValue("staleBefore", OffsetDateTime.now(ZoneOffset.UTC).minusHours(staleAfterHours));
        }
        return params;
    }

    private int normalizeStaleAfterHours(Integer staleAfterHours) {
        if (staleAfterHours == null || staleAfterHours <= 0) {
            return DEFAULT_STALE_AFTER_HOURS;
        }
        return Math.min(staleAfterHours, MAX_STALE_AFTER_HOURS);
    }

    private int normalizeTopLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_TOP_LIMIT;
        }
        return Math.min(limit, MAX_TOP_LIMIT);
    }

    private int normalizeTrendDays(Integer days) {
        if (days == null || days <= 0) {
            return DEFAULT_TREND_DAYS;
        }
        return Math.min(days, MAX_TREND_DAYS);
    }

    private int clampLength(int length) {
        if (length <= 0) {
            return 25;
        }
        return Math.min(length, 200);
    }

    private String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
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

    private Long longObjectValue(Object value) {
        if (value == null) {
            return null;
        }
        return longValue(value);
    }

    private double doubleValue(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Double.parseDouble(text);
        }
        return 0D;
    }

    private String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
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

    private LocalDate localDateValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        if (value instanceof Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        if (value instanceof String text && !text.isBlank()) {
            return LocalDate.parse(text);
        }
        return null;
    }
}
