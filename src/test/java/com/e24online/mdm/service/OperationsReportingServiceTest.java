package com.e24online.mdm.service;

import com.e24online.mdm.records.operations.PipelineDailyTrendResponse;
import com.e24online.mdm.records.operations.PipelineFailureCategoryResponse;
import com.e24online.mdm.records.operations.PipelineOperabilitySummaryResponse;
import com.e24online.mdm.records.ui.DataTableResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
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
class OperationsReportingServiceTest {

    @Mock
    private NamedParameterJdbcTemplate jdbc;

    private OperationsReportingService service;

    @BeforeEach
    void setUp() {
        service = new OperationsReportingService(
                jdbc,
                new BlockingDb(Schedulers.immediate())
        );
    }

    @Test
    void getPipelineSummary_mapsAggregateCounts() {
        OffsetDateTime oldestInFlight = OffsetDateTime.now().minusHours(3);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("in_flight_payloads", 4L);
        row.put("received_payloads", 1L);
        row.put("queued_payloads", 2L);
        row.put("validated_payloads", 1L);
        row.put("failed_payloads", 7L);
        row.put("failed_last_24_hours", 3L);
        row.put("queue_failures_last_7_days", 2L);
        row.put("evaluation_failures_last_7_days", 5L);
        row.put("oldest_in_flight_received_at", oldestInFlight);
        when(jdbc.queryForMap(anyString(), any(MapSqlParameterSource.class))).thenReturn(row);

        PipelineOperabilitySummaryResponse response = service.getPipelineSummary("tenant-a").block();

        assertNotNull(response);
        assertEquals(4L, response.inFlightPayloads());
        assertEquals(1L, response.receivedPayloads());
        assertEquals(2L, response.queuedPayloads());
        assertEquals(1L, response.validatedPayloads());
        assertEquals(7L, response.failedPayloads());
        assertEquals(3L, response.failedLast24Hours());
        assertEquals(2L, response.queueFailuresLast7Days());
        assertEquals(5L, response.evaluationFailuresLast7Days());
        assertEquals(oldestInFlight, response.oldestInFlightReceivedAt());
        assertTrue(response.oldestInFlightAgeMinutes() >= 180L);
    }

    @Test
    void getPipelineSummary_avoidsUntypedNullTenantPredicate() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("in_flight_payloads", 0L);
        row.put("received_payloads", 0L);
        row.put("queued_payloads", 0L);
        row.put("validated_payloads", 0L);
        row.put("failed_payloads", 0L);
        row.put("failed_last_24_hours", 0L);
        row.put("queue_failures_last_7_days", 0L);
        row.put("evaluation_failures_last_7_days", 0L);
        row.put("oldest_in_flight_received_at", null);
        when(jdbc.queryForMap(anyString(), any(MapSqlParameterSource.class))).thenReturn(row);

        service.getPipelineSummary("tenant-a").block();

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
    void getFailureCategories_mapsRows() {
        OffsetDateTime latestFailure = OffsetDateTime.now().minusHours(1);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("category_key", "QUEUE_PUBLISH");
        row.put("failure_count", 3L);
        row.put("latest_failure_at", latestFailure);
        row.put("sample_process_error", "Queue publish failed: broker unavailable");
        when(jdbc.queryForList(anyString(), any(MapSqlParameterSource.class))).thenReturn(List.of(row));

        List<PipelineFailureCategoryResponse> response = service.getFailureCategories("tenant-a", 7, 6).block();

        assertNotNull(response);
        assertEquals(1, response.size());
        assertEquals("QUEUE_PUBLISH", response.getFirst().categoryKey());
        assertEquals(3L, response.getFirst().failureCount());
        assertEquals(latestFailure, response.getFirst().latestFailureAt());
        assertEquals("Queue publish failed: broker unavailable", response.getFirst().sampleProcessError());
    }

    @Test
    void getPipelineTrend_mapsRows() {
        LocalDate bucketDate = LocalDate.now().minusDays(1);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("bucket_date", bucketDate);
        row.put("ingest_success_count", 12L);
        row.put("queue_success_count", 10L);
        row.put("queue_failure_count", 2L);
        row.put("evaluation_success_count", 9L);
        row.put("evaluation_failure_count", 1L);
        row.put("failed_payload_count", 3L);
        when(jdbc.queryForList(anyString(), any(MapSqlParameterSource.class))).thenReturn(List.of(row));

        List<PipelineDailyTrendResponse> response = service.getPipelineTrend("tenant-a", 7).block();

        assertNotNull(response);
        assertEquals(1, response.size());
        assertEquals(bucketDate, response.getFirst().bucketDate());
        assertEquals(12L, response.getFirst().ingestSuccessCount());
        assertEquals(10L, response.getFirst().queueSuccessCount());
        assertEquals(2L, response.getFirst().queueFailureCount());
        assertEquals(9L, response.getFirst().evaluationSuccessCount());
        assertEquals(1L, response.getFirst().evaluationFailureCount());
        assertEquals(3L, response.getFirst().failedPayloadCount());
    }

    @Test
    void getFailedPayloadsTable_returnsPagedRows() {
        when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class)))
                .thenReturn(2L);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", 91L);
        row.put("tenant_id", "tenant-a");
        row.put("device_external_id", "dev-1");
        row.put("agent_id", "agent-1");
        row.put("payload_version", "v1");
        row.put("agent_version", "6.3.0");
        row.put("schema_compatibility_status", "SUPPORTED");
        row.put("process_status", "FAILED");
        row.put("failure_category", "EVALUATION_PROCESSING");
        row.put("process_error", "Posture evaluation failed");
        row.put("received_at", OffsetDateTime.now().minusHours(2));
        row.put("processed_at", OffsetDateTime.now().minusHours(1));
        when(jdbc.queryForList(anyString(), any(MapSqlParameterSource.class))).thenReturn(List.of(row));

        DataTableResponse<Map<String, Object>> response = service.getFailedPayloadsTable(
                "tenant-a",
                5,
                0,
                25,
                7,
                "dev",
                "processed_at",
                "desc"
        ).block();

        assertNotNull(response);
        assertEquals(5, response.draw());
        assertEquals(2L, response.recordsTotal());
        assertEquals(2L, response.recordsFiltered());
        assertEquals(1, response.data().size());
        assertEquals("tenant-a", response.data().getFirst().get("tenant_id"));
        assertEquals("EVALUATION_PROCESSING", response.data().getFirst().get("failure_category"));
    }
}
