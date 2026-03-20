package com.e24online.mdm.service;

import com.e24online.mdm.records.operations.PipelineDailyTrendResponse;
import com.e24online.mdm.records.operations.PipelineFailureCategoryResponse;
import com.e24online.mdm.records.operations.PipelineOperabilitySummaryResponse;
import com.e24online.mdm.records.ui.DataTableResponse;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Duration;
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
public class OperationsReportingService {

    private static final int DEFAULT_FAILURE_DAYS = 7;
    private static final int MAX_FAILURE_DAYS = 30;
    private static final int DEFAULT_FAILURE_LIMIT = 6;
    private static final int MAX_FAILURE_LIMIT = 20;
    private static final int DEFAULT_TREND_DAYS = 7;
    private static final int MAX_TREND_DAYS = 30;
    private static final String FAILED_PAYLOAD_CATEGORY_EXPR = """
            CASE
                WHEN LOWER(COALESCE(payload.process_error, '')) LIKE 'queue publish failed:%'
                    THEN 'QUEUE_PUBLISH'
                WHEN COALESCE(NULLIF(payload.schema_compatibility_status, ''), 'UNVERIFIED') = 'UNVERIFIED'
                    THEN 'UNVERIFIED_CONTRACT'
                WHEN LOWER(COALESCE(payload.process_error, '')) LIKE '%payload_json%'
                    OR LOWER(COALESCE(payload.process_error, '')) LIKE '%capture_time%'
                    OR LOWER(COALESCE(payload.process_error, '')) LIKE '%payload_version%'
                    OR LOWER(COALESCE(payload.process_error, '')) LIKE '%agent_version%'
                    OR LOWER(COALESCE(payload.process_error, '')) LIKE '% is required%'
                    OR LOWER(COALESCE(payload.process_error, '')) LIKE '%could not be parsed%'
                    OR LOWER(COALESCE(payload.process_error, '')) LIKE '%exceeds max allowed size%'
                    THEN 'PAYLOAD_CONTRACT'
                WHEN LOWER(COALESCE(payload.process_error, '')) LIKE '%invalid payload_json%'
                    OR LOWER(COALESCE(payload.process_error, '')) LIKE '%invalid json%'
                    OR LOWER(COALESCE(payload.process_error, '')) LIKE '%parse%'
                    THEN 'PAYLOAD_PARSE'
                WHEN LOWER(COALESCE(payload.process_error, '')) LIKE '%not found%'
                    OR LOWER(COALESCE(payload.process_error, '')) LIKE '%missing%'
                    THEN 'MISSING_REFERENCE'
                WHEN LOWER(COALESCE(payload.process_error, '')) LIKE '%timeout%'
                    OR LOWER(COALESCE(payload.process_error, '')) LIKE '%timed out%'
                    OR LOWER(COALESCE(payload.process_error, '')) LIKE '%connection%'
                    OR LOWER(COALESCE(payload.process_error, '')) LIKE '%deadlock%'
                    OR LOWER(COALESCE(payload.process_error, '')) LIKE '%server is busy%'
                    OR LOWER(COALESCE(payload.process_error, '')) LIKE '%service unavailable%'
                    THEN 'INFRASTRUCTURE_RETRY'
                WHEN NULLIF(BTRIM(COALESCE(payload.process_error, '')), '') IS NULL
                    THEN 'UNKNOWN'
                ELSE 'EVALUATION_PROCESSING'
            END
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final BlockingDb blockingDb;

    public OperationsReportingService(NamedParameterJdbcTemplate jdbc,
                                      BlockingDb blockingDb) {
        this.jdbc = jdbc;
        this.blockingDb = blockingDb;
    }

    public Mono<PipelineOperabilitySummaryResponse> getPipelineSummary(String tenantId) {
        return blockingDb.mono(() -> getPipelineSummaryBlocking(tenantId));
    }

    public Mono<List<PipelineFailureCategoryResponse>> getFailureCategories(String tenantId,
                                                                            Integer days,
                                                                            Integer limit) {
        int normalizedDays = normalizeDays(days, DEFAULT_FAILURE_DAYS, MAX_FAILURE_DAYS);
        int normalizedLimit = normalizeLimit(limit, DEFAULT_FAILURE_LIMIT, MAX_FAILURE_LIMIT);
        return blockingDb.mono(() -> getFailureCategoriesBlocking(tenantId, normalizedDays, normalizedLimit));
    }

    public Mono<List<PipelineDailyTrendResponse>> getPipelineTrend(String tenantId, Integer days) {
        int normalizedDays = normalizeDays(days, DEFAULT_TREND_DAYS, MAX_TREND_DAYS);
        return blockingDb.mono(() -> getPipelineTrendBlocking(tenantId, normalizedDays));
    }

    public Mono<DataTableResponse<Map<String, Object>>> getFailedPayloadsTable(String tenantId,
                                                                               int draw,
                                                                               int start,
                                                                               int length,
                                                                               Integer days,
                                                                               String search,
                                                                               String sortBy,
                                                                               String sortDir) {
        int normalizedDays = normalizeDays(days, DEFAULT_FAILURE_DAYS, MAX_FAILURE_DAYS);
        return blockingDb.mono(() -> getFailedPayloadsTableBlocking(
                tenantId,
                draw,
                start,
                length,
                normalizedDays,
                search,
                sortBy,
                sortDir
        ));
    }

    private PipelineOperabilitySummaryResponse getPipelineSummaryBlocking(String tenantId) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        MapSqlParameterSource params = tenantParams(tenantId)
                .addValue("failedSince", now.minusHours(24))
                .addValue("recentSince", now.minusDays(7));

        Map<String, Object> row = jdbc.queryForMap(
                """
                        WITH payload_summary AS (
                            SELECT
                                COUNT(*) FILTER (WHERE process_status IN ('RECEIVED', 'QUEUED', 'VALIDATED')) AS in_flight_payloads,
                                COUNT(*) FILTER (WHERE process_status = 'RECEIVED') AS received_payloads,
                                COUNT(*) FILTER (WHERE process_status = 'QUEUED') AS queued_payloads,
                                COUNT(*) FILTER (WHERE process_status = 'VALIDATED') AS validated_payloads,
                                COUNT(*) FILTER (WHERE process_status = 'FAILED') AS failed_payloads,
                                COUNT(*) FILTER (
                                    WHERE process_status = 'FAILED'
                                      AND COALESCE(processed_at, received_at) >= :failedSince
                                ) AS failed_last_24_hours,
                                MIN(received_at) FILTER (
                                    WHERE process_status IN ('RECEIVED', 'QUEUED', 'VALIDATED')
                                ) AS oldest_in_flight_received_at
                            FROM device_posture_payload
                            WHERE (:tenantFilterDisabled = TRUE OR LOWER(COALESCE(tenant_id, '')) = :tenantId)
                        ),
                        audit_summary AS (
                            SELECT
                                SUM(CASE
                                    WHEN event_type = 'POSTURE_EVALUATION_QUEUED' AND status = 'FAILURE'
                                    THEN 1 ELSE 0 END
                                ) AS queue_failures_last_7_days,
                                SUM(CASE
                                    WHEN event_type = 'POSTURE_EVALUATED' AND status = 'FAILURE'
                                    THEN 1 ELSE 0 END
                                ) AS evaluation_failures_last_7_days
                            FROM audit_event_log
                            WHERE event_category = 'POSTURE'
                              AND created_at >= :recentSince
                              AND (:tenantFilterDisabled = TRUE OR LOWER(COALESCE(tenant_id, '')) = :tenantId)
                        )
                        SELECT
                            payload_summary.in_flight_payloads,
                            payload_summary.received_payloads,
                            payload_summary.queued_payloads,
                            payload_summary.validated_payloads,
                            payload_summary.failed_payloads,
                            payload_summary.failed_last_24_hours,
                            payload_summary.oldest_in_flight_received_at,
                            audit_summary.queue_failures_last_7_days,
                            audit_summary.evaluation_failures_last_7_days
                        FROM payload_summary
                        CROSS JOIN audit_summary
                        """,
                params
        );

        OffsetDateTime oldestInFlight = offsetDateTimeValue(row.get("oldest_in_flight_received_at"));
        Long oldestAgeMinutes = oldestInFlight == null
                ? null
                : Math.max(0L, Duration.between(oldestInFlight, now).toMinutes());

        return new PipelineOperabilitySummaryResponse(
                now,
                longValue(row.get("in_flight_payloads")),
                longValue(row.get("received_payloads")),
                longValue(row.get("queued_payloads")),
                longValue(row.get("validated_payloads")),
                longValue(row.get("failed_payloads")),
                longValue(row.get("failed_last_24_hours")),
                longValue(row.get("queue_failures_last_7_days")),
                longValue(row.get("evaluation_failures_last_7_days")),
                oldestInFlight,
                oldestAgeMinutes
        );
    }

    private List<PipelineFailureCategoryResponse> getFailureCategoriesBlocking(String tenantId,
                                                                               int days,
                                                                               int limit) {
        MapSqlParameterSource params = tenantParams(tenantId)
                .addValue("failureSince", OffsetDateTime.now(ZoneOffset.UTC).minusDays(days))
                .addValue("limit", limit);

        List<Map<String, Object>> rows = jdbc.queryForList(
                """
                        WITH failed_payloads AS (
                            SELECT
                                payload.id,
                                payload.process_error,
                                COALESCE(payload.processed_at, payload.received_at) AS failure_at,
                        """ + FAILED_PAYLOAD_CATEGORY_EXPR + """
                                 AS failure_category
                            FROM device_posture_payload payload
                            WHERE payload.process_status = 'FAILED'
                              AND COALESCE(payload.processed_at, payload.received_at) >= :failureSince
                              AND (:tenantFilterDisabled = TRUE OR LOWER(COALESCE(payload.tenant_id, '')) = :tenantId)
                        ),
                        ranked_failures AS (
                            SELECT
                                failed_payloads.*,
                                ROW_NUMBER() OVER (
                                    PARTITION BY failed_payloads.failure_category
                                    ORDER BY failed_payloads.failure_at DESC, failed_payloads.id DESC
                                ) AS rn
                            FROM failed_payloads
                        )
                        SELECT
                            failure_category AS category_key,
                            COUNT(*) AS failure_count,
                            MAX(failure_at) AS latest_failure_at,
                            MAX(CASE WHEN rn = 1 THEN process_error END) AS sample_process_error
                        FROM ranked_failures
                        GROUP BY failure_category
                        ORDER BY failure_count DESC, latest_failure_at DESC, category_key ASC
                        LIMIT :limit
                        """,
                params
        );

        return rows.stream()
                .map(row -> new PipelineFailureCategoryResponse(
                        stringValue(row.get("category_key")),
                        longValue(row.get("failure_count")),
                        offsetDateTimeValue(row.get("latest_failure_at")),
                        stringValue(row.get("sample_process_error"))
                ))
                .toList();
    }

    private List<PipelineDailyTrendResponse> getPipelineTrendBlocking(String tenantId, int days) {
        LocalDate endDate = OffsetDateTime.now(ZoneOffset.UTC).toLocalDate();
        LocalDate startDate = endDate.minusDays(days - 1L);
        OffsetDateTime trendStart = startDate.atStartOfDay().atOffset(ZoneOffset.UTC);
        MapSqlParameterSource params = tenantParams(tenantId)
                .addValue("startDate", startDate)
                .addValue("endDate", endDate)
                .addValue("trendStart", trendStart);

        List<Map<String, Object>> rows = jdbc.queryForList(
                """
                        WITH day_buckets AS (
                            SELECT generate_series(:startDate::date, :endDate::date, interval '1 day')::date AS bucket_date
                        ),
                        audit_rollup AS (
                            SELECT
                                DATE_TRUNC('day', created_at AT TIME ZONE 'UTC')::date AS bucket_date,
                                SUM(CASE
                                    WHEN event_type = 'POSTURE_PAYLOAD_INGESTED' AND status = 'SUCCESS'
                                    THEN 1 ELSE 0 END
                                ) AS ingest_success_count,
                                SUM(CASE
                                    WHEN event_type = 'POSTURE_EVALUATION_QUEUED' AND status = 'SUCCESS'
                                    THEN 1 ELSE 0 END
                                ) AS queue_success_count,
                                SUM(CASE
                                    WHEN event_type = 'POSTURE_EVALUATION_QUEUED' AND status = 'FAILURE'
                                    THEN 1 ELSE 0 END
                                ) AS queue_failure_count,
                                SUM(CASE
                                    WHEN event_type = 'POSTURE_EVALUATED' AND status = 'SUCCESS'
                                    THEN 1 ELSE 0 END
                                ) AS evaluation_success_count,
                                SUM(CASE
                                    WHEN event_type = 'POSTURE_EVALUATED' AND status = 'FAILURE'
                                    THEN 1 ELSE 0 END
                                ) AS evaluation_failure_count
                            FROM audit_event_log
                            WHERE event_category = 'POSTURE'
                              AND created_at >= :trendStart
                              AND (:tenantFilterDisabled = TRUE OR LOWER(COALESCE(tenant_id, '')) = :tenantId)
                            GROUP BY DATE_TRUNC('day', created_at AT TIME ZONE 'UTC')::date
                        ),
                        payload_failures AS (
                            SELECT
                                DATE_TRUNC('day', COALESCE(processed_at, received_at) AT TIME ZONE 'UTC')::date AS bucket_date,
                                COUNT(*) FILTER (WHERE process_status = 'FAILED') AS failed_payload_count
                            FROM device_posture_payload
                            WHERE COALESCE(processed_at, received_at) >= :trendStart
                              AND (:tenantFilterDisabled = TRUE OR LOWER(COALESCE(tenant_id, '')) = :tenantId)
                            GROUP BY DATE_TRUNC('day', COALESCE(processed_at, received_at) AT TIME ZONE 'UTC')::date
                        )
                        SELECT
                            buckets.bucket_date,
                            COALESCE(audit_rollup.ingest_success_count, 0) AS ingest_success_count,
                            COALESCE(audit_rollup.queue_success_count, 0) AS queue_success_count,
                            COALESCE(audit_rollup.queue_failure_count, 0) AS queue_failure_count,
                            COALESCE(audit_rollup.evaluation_success_count, 0) AS evaluation_success_count,
                            COALESCE(audit_rollup.evaluation_failure_count, 0) AS evaluation_failure_count,
                            COALESCE(payload_failures.failed_payload_count, 0) AS failed_payload_count
                        FROM day_buckets buckets
                        LEFT JOIN audit_rollup
                          ON audit_rollup.bucket_date = buckets.bucket_date
                        LEFT JOIN payload_failures
                          ON payload_failures.bucket_date = buckets.bucket_date
                        ORDER BY buckets.bucket_date ASC
                        """,
                params
        );

        return rows.stream()
                .map(row -> new PipelineDailyTrendResponse(
                        localDateValue(row.get("bucket_date")),
                        longValue(row.get("ingest_success_count")),
                        longValue(row.get("queue_success_count")),
                        longValue(row.get("queue_failure_count")),
                        longValue(row.get("evaluation_success_count")),
                        longValue(row.get("evaluation_failure_count")),
                        longValue(row.get("failed_payload_count"))
                ))
                .toList();
    }

    private DataTableResponse<Map<String, Object>> getFailedPayloadsTableBlocking(String tenantId,
                                                                                  int draw,
                                                                                  int start,
                                                                                  int length,
                                                                                  int days,
                                                                                  String search,
                                                                                  String sortBy,
                                                                                  String sortDir) {
        MapSqlParameterSource params = tenantParams(tenantId)
                .addValue("failureSince", OffsetDateTime.now(ZoneOffset.UTC).minusDays(days));
        String searchPredicate = failedPayloadSearchPredicate(search, params);
        String orderBy = resolveFailedPayloadOrderColumn(sortBy);
        String orderDir = resolveSortDirection(sortDir);
        int safeLength = clampLength(length);
        int safeStart = Math.max(start, 0);

        Long total = jdbc.queryForObject(
                failedPayloadBaseSql("""
                        SELECT COUNT(*)
                        FROM failed_payloads
                        WHERE 1 = 1
                        """) + searchPredicate,
                params,
                Long.class
        );
        long recordsTotal = total == null ? 0L : total;
        long recordsFiltered = recordsTotal;

        params.addValue("limit", safeLength);
        params.addValue("offset", safeStart);

        List<Map<String, Object>> data = jdbc.queryForList(
                failedPayloadBaseSql("""
                        SELECT
                            id,
                            tenant_id,
                            device_external_id,
                            agent_id,
                            payload_version,
                            agent_version,
                            schema_compatibility_status,
                            process_status,
                            failure_category,
                            process_error,
                            received_at,
                            processed_at
                        FROM failed_payloads
                        WHERE 1 = 1
                        """) + searchPredicate + """
                        ORDER BY """ + orderBy + " " + orderDir + ", id DESC" + """
                        LIMIT :limit OFFSET :offset
                        """,
                params
        );

        return new DataTableResponse<>(draw, recordsTotal, recordsFiltered, data);
    }

    private String failedPayloadBaseSql(String selectSql) {
        return """
                WITH failed_payloads AS (
                    SELECT
                        payload.id,
                        COALESCE(payload.tenant_id, '') AS tenant_id,
                        payload.device_external_id,
                        payload.agent_id,
                        payload.payload_version,
                        payload.agent_version,
                        COALESCE(NULLIF(payload.schema_compatibility_status, ''), 'UNVERIFIED') AS schema_compatibility_status,
                        payload.process_status,
                        payload.process_error,
                        payload.received_at,
                        payload.processed_at,
                """ + FAILED_PAYLOAD_CATEGORY_EXPR + """
                         AS failure_category
                    FROM device_posture_payload payload
                    WHERE payload.process_status = 'FAILED'
                      AND COALESCE(payload.processed_at, payload.received_at) >= :failureSince
                      AND (:tenantFilterDisabled = TRUE OR LOWER(COALESCE(payload.tenant_id, '')) = :tenantId)
                )
                """ + selectSql;
    }

    private String failedPayloadSearchPredicate(String search, MapSqlParameterSource params) {
        String term = normalizeOptionalText(search);
        if (term == null) {
            return "";
        }
        params.addValue("searchTerm", "%" + term.toLowerCase(Locale.ROOT) + "%");
        return """
                 AND (
                    LOWER(CAST(tenant_id AS TEXT)) LIKE :searchTerm
                    OR LOWER(CAST(device_external_id AS TEXT)) LIKE :searchTerm
                    OR LOWER(CAST(agent_id AS TEXT)) LIKE :searchTerm
                    OR LOWER(CAST(payload_version AS TEXT)) LIKE :searchTerm
                    OR LOWER(CAST(agent_version AS TEXT)) LIKE :searchTerm
                    OR LOWER(CAST(schema_compatibility_status AS TEXT)) LIKE :searchTerm
                    OR LOWER(CAST(failure_category AS TEXT)) LIKE :searchTerm
                    OR LOWER(CAST(process_error AS TEXT)) LIKE :searchTerm
                 )
                """;
    }

    private String resolveFailedPayloadOrderColumn(String sortBy) {
        Map<String, String> sortableColumns = new LinkedHashMap<>();
        sortableColumns.put("id", "id");
        sortableColumns.put("tenant_id", "tenant_id");
        sortableColumns.put("device_external_id", "device_external_id");
        sortableColumns.put("agent_id", "agent_id");
        sortableColumns.put("payload_version", "payload_version");
        sortableColumns.put("agent_version", "agent_version");
        sortableColumns.put("schema_compatibility_status", "schema_compatibility_status");
        sortableColumns.put("process_status", "process_status");
        sortableColumns.put("failure_category", "failure_category");
        sortableColumns.put("received_at", "received_at");
        sortableColumns.put("processed_at", "processed_at");
        String normalized = normalizeOptionalText(sortBy);
        if (normalized == null) {
            return "processed_at";
        }
        return sortableColumns.getOrDefault(normalized, "processed_at");
    }

    private String resolveSortDirection(String sortDir) {
        String normalized = normalizeOptionalText(sortDir);
        return "ASC".equalsIgnoreCase(normalized) ? "ASC" : "DESC";
    }

    private MapSqlParameterSource tenantParams(String tenantId) {
        String normalizedTenantId = normalizeOptionalText(tenantId);
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("tenantId", normalizedTenantId == null ? null : normalizedTenantId.toLowerCase(Locale.ROOT));
        params.addValue("tenantFilterDisabled", normalizedTenantId == null);
        return params;
    }

    private int normalizeDays(Integer days, int defaultDays, int maxDays) {
        if (days == null || days <= 0) {
            return defaultDays;
        }
        return Math.min(days, maxDays);
    }

    private int normalizeLimit(Integer limit, int defaultLimit, int maxLimit) {
        if (limit == null || limit <= 0) {
            return defaultLimit;
        }
        return Math.min(limit, maxLimit);
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
