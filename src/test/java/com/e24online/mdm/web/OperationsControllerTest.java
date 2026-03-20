package com.e24online.mdm.web;

import com.e24online.mdm.records.operations.QueueHealthEntryResponse;
import com.e24online.mdm.records.operations.QueueHealthSummaryResponse;
import com.e24online.mdm.records.operations.PipelineDailyTrendResponse;
import com.e24online.mdm.records.operations.PipelineFailureCategoryResponse;
import com.e24online.mdm.records.operations.PipelineOperabilitySummaryResponse;
import com.e24online.mdm.records.ui.DataTableResponse;
import com.e24online.mdm.service.OperationsReportingService;
import com.e24online.mdm.service.QueueHealthService;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OperationsControllerTest {

    @Mock
    private QueueHealthService queueHealthService;

    @Mock
    private OperationsReportingService operationsReportingService;

    @Mock
    private AuthenticatedRequestContext requestContext;

    @Mock
    private Authentication authentication;

    private OperationsController controller;

    @BeforeEach
    void setUp() {
        controller = new OperationsController(queueHealthService, operationsReportingService, requestContext);
    }

    @Test
    void getQueueHealthSummary_delegatesToService() {
        QueueHealthSummaryResponse summary = new QueueHealthSummaryResponse(
                OffsetDateTime.now(),
                "DEGRADED",
                5L,
                1L,
                List.of(new QueueHealthEntryResponse(
                        "POSTURE_EVALUATION",
                        "posture.queue",
                        "posture.dlq",
                        5L,
                        1L,
                        2L,
                        2,
                        6,
                        "DLQ_BACKLOG",
                        null
                ))
        );
        when(queueHealthService.getQueueHealthSummary()).thenReturn(Mono.just(summary));

        QueueHealthSummaryResponse response = controller.getQueueHealthSummary().block();

        assertNotNull(response);
        assertEquals(summary, response);
    }

    @Test
    void pipelineEndpoints_resolveTenantAndDelegate() {
        OffsetDateTime now = OffsetDateTime.now();
        PipelineOperabilitySummaryResponse summary = new PipelineOperabilitySummaryResponse(
                now,
                4L,
                1L,
                2L,
                1L,
                7L,
                3L,
                2L,
                5L,
                now.minusHours(2),
                120L
        );
        List<PipelineFailureCategoryResponse> categories = List.of(new PipelineFailureCategoryResponse(
                "QUEUE_PUBLISH",
                3L,
                now.minusMinutes(30),
                "Queue publish failed: broker unavailable"
        ));
        List<PipelineDailyTrendResponse> trend = List.of(new PipelineDailyTrendResponse(
                LocalDate.now().minusDays(1),
                12L,
                10L,
                2L,
                9L,
                1L,
                3L
        ));
        DataTableResponse<Map<String, Object>> failedPayloads = new DataTableResponse<>(
                2,
                4L,
                4L,
                List.of(Map.of("id", 91L, "tenant_id", "tenant-a", "failure_category", "QUEUE_PUBLISH"))
        );

        when(requestContext.resolveOptionalTenantId(authentication, null)).thenReturn(Mono.just("tenant-a"));
        when(operationsReportingService.getPipelineSummary("tenant-a")).thenReturn(Mono.just(summary));
        when(operationsReportingService.getFailureCategories("tenant-a", 7, 6)).thenReturn(Mono.just(categories));
        when(operationsReportingService.getPipelineTrend("tenant-a", 7)).thenReturn(Mono.just(trend));
        when(operationsReportingService.getFailedPayloadsTable("tenant-a", 2, 0, 25, 7, "dev", "processed_at", "desc"))
                .thenReturn(Mono.just(failedPayloads));

        PipelineOperabilitySummaryResponse summaryResponse = controller.getPipelineSummary(authentication, null).block();
        List<PipelineFailureCategoryResponse> categoryResponse = controller
                .getFailureCategories(authentication, null, 7, 6)
                .block();
        List<PipelineDailyTrendResponse> trendResponse = controller
                .getPipelineTrend(authentication, null, 7)
                .block();
        DataTableResponse<Map<String, Object>> tableResponse = controller
                .getFailedPayloadsTable(authentication, null, 2, 0, 25, 7, "dev", "processed_at", "desc")
                .block();

        assertEquals(summary, summaryResponse);
        assertEquals(categories, categoryResponse);
        assertEquals(trend, trendResponse);
        assertNotNull(tableResponse);
        assertEquals(2, tableResponse.draw());
        assertEquals(1, tableResponse.data().size());
    }
}
