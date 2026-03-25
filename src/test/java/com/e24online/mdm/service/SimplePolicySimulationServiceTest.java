package com.e24online.mdm.service;

import com.e24online.mdm.domain.DeviceInstalledApplication;
import com.e24online.mdm.domain.DeviceTrustProfile;
import com.e24online.mdm.records.posture.evaluation.EvaluationComputation;
import com.e24online.mdm.records.posture.evaluation.LifecycleResolution;
import com.e24online.mdm.records.posture.evaluation.MatchDraft;
import com.e24online.mdm.records.posture.evaluation.ParsedPosture;
import com.e24online.mdm.records.posture.evaluation.ScoreSignal;
import com.e24online.mdm.service.evaluation.EvaluationEngineService;
import com.e24online.mdm.web.dto.SimplePolicySimulationAppInput;
import com.e24online.mdm.web.dto.SimplePolicySimulationRequest;
import com.e24online.mdm.web.dto.SimplePolicySimulationResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.scheduler.Schedulers;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SimplePolicySimulationServiceTest {

    @Mock
    private DeviceStateService deviceStateService;

    @Mock
    private EvaluationEngineService evaluationEngineService;

    private SimplePolicySimulationService service;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        service = new SimplePolicySimulationService(
                deviceStateService,
                evaluationEngineService,
                new BlockingDb(Schedulers.immediate()),
                objectMapper
        );
    }

    @Test
    void simulate_mapsRequestIntoEvaluationAndReturnsReadableResponse() {
        OffsetDateTime now = OffsetDateTime.now();
        ObjectNode root = objectMapper.createObjectNode();
        root.put("os_type", "ANDROID");
        root.put("root_detected", true);
        ArrayNode apps = objectMapper.createArrayNode();
        ParsedPosture parsed = new ParsedPosture(
                "tenant-a",
                "simulated-device",
                "simple-policy-simulator",
                "ANDROID",
                "ANDROID",
                "14",
                null,
                "PHONE",
                "UTC",
                null,
                34,
                null,
                "Google",
                true,
                false,
                false,
                now,
                root,
                apps
        );

        EvaluationComputation computation = new EvaluationComputation(
                (short) 100,
                (short) 40,
                (short) -60,
                1,
                1,
                "BLOCK",
                "Policy decision",
                true,
                9L,
                List.of(
                        new MatchDraft("SYSTEM_RULE", 1L, null, null, null, "SUPPORTED", null, (short) 5, "BLOCK", (short) -60, "{\"rule_tag\":\"Rooted Android device\",\"rule_code\":\"SR-1\"}"),
                        new MatchDraft("REJECT_APPLICATION", null, 2L, null, null, "SUPPORTED", null, (short) 4, "BLOCK", (short) -40, "{\"app_name\":\"AnyDesk\",\"package_id\":\"AnyDeskSoftwareGmbH.AnyDesk\"}")
                ),
                List.of(
                        new ScoreSignal("SYSTEM_RULE", 1L, null, 1L, null, null, "SUPPORTED", (short) -60, "Matched system rule"),
                        new ScoreSignal("REJECT_APPLICATION", 2L, null, null, 2L, null, "SUPPORTED", (short) -40, "Matched reject app")
                )
        );

        when(deviceStateService.parsePosture(any(), any(), any(), any(), any())).thenReturn(parsed);
        when(deviceStateService.resolveLifecycle(any(), any())).thenReturn(new LifecycleResolution(3L, "SUPPORTED", "OS_SUPPORTED"));
        when(evaluationEngineService.computeEvaluation(any(DeviceTrustProfile.class), any(ParsedPosture.class), anyList(), any(LifecycleResolution.class), any()))
                .thenReturn(computation);

        SimplePolicySimulationRequest request = new SimplePolicySimulationRequest();
        request.setCurrentScore(100);
        request.setOsType("ANDROID");
        request.setOsName("ANDROID");
        request.setOsVersion("14");
        request.setDeviceType("PHONE");
        request.setApiLevel(34);
        request.setManufacturer("Google");
        request.setRootDetected(true);

        SimplePolicySimulationAppInput app = new SimplePolicySimulationAppInput();
        app.setAppName("AnyDesk");
        app.setPackageId("AnyDeskSoftwareGmbH.AnyDesk");
        app.setAppVersion("1.0.0");
        app.setAppOsType("ANDROID");
        request.setInstalledApps(List.of(app));

        SimplePolicySimulationResponse response = service.simulate("tenant-a", reactor.core.publisher.Mono.just(request)).block();

        assertNotNull(response);
        assertEquals((short) 100, response.getScoreBefore());
        assertEquals((short) 40, response.getScoreAfter());
        assertEquals("BLOCK", response.getDecisionAction());
        assertEquals("SUPPORTED", response.getLifecycleState());
        assertEquals(2, response.getFindings().size());
        assertEquals("DEVICE_CHECK", response.getFindings().getFirst().getCategory());
        assertEquals("Rooted Android device", response.getFindings().getFirst().getTitle());
        assertEquals("APP_RULE", response.getFindings().get(1).getCategory());
        assertEquals("AnyDesk", response.getFindings().get(1).getTitle());

        ArgumentCaptor<List<DeviceInstalledApplication>> appsCaptor = ArgumentCaptor.forClass(List.class);
        verify(evaluationEngineService).computeEvaluation(any(DeviceTrustProfile.class), any(ParsedPosture.class), appsCaptor.capture(), any(LifecycleResolution.class), any());
        assertEquals(1, appsCaptor.getValue().size());
        assertEquals("AnyDesk", appsCaptor.getValue().getFirst().getAppName());
    }

    @Test
    void simulate_addsLifecycleFallbackFindingWhenOnlyPostureSignalExists() {
        ParsedPosture parsed = new ParsedPosture(
                null,
                "simulated-device",
                "simple-policy-simulator",
                "WINDOWS",
                "WINDOWS 11",
                "23H2",
                null,
                "LAPTOP",
                "UTC",
                null,
                null,
                null,
                "Dell",
                false,
                false,
                false,
                OffsetDateTime.now(),
                objectMapper.createObjectNode().put("os_type", "WINDOWS"),
                objectMapper.createArrayNode()
        );

        when(deviceStateService.parsePosture(any(), any(), any(), any(), any())).thenReturn(parsed);
        when(deviceStateService.resolveLifecycle(any(), any())).thenReturn(new LifecycleResolution(8L, "EEOL", "OS_EEOL"));
        when(evaluationEngineService.computeEvaluation(any(DeviceTrustProfile.class), any(ParsedPosture.class), anyList(), any(LifecycleResolution.class), any()))
                .thenReturn(new EvaluationComputation(
                        (short) 90,
                        (short) 75,
                        (short) -15,
                        0,
                        0,
                        "NOTIFY",
                        "Auto decision from evaluated trust score",
                        false,
                        null,
                        List.of(),
                        List.of(new ScoreSignal("POSTURE_SIGNAL", 8L, null, null, null, 8L, "EEOL", (short) -15, "Lifecycle posture signal: OS_EEOL"))
                ));

        SimplePolicySimulationRequest request = new SimplePolicySimulationRequest();
        request.setCurrentScore(90);
        request.setOsType("WINDOWS");

        SimplePolicySimulationResponse response = service.simulate(null, reactor.core.publisher.Mono.just(request)).block();

        assertNotNull(response);
        assertEquals(1, response.getFindings().size());
        assertEquals("LIFECYCLE", response.getFindings().getFirst().getCategory());
        assertFalse(response.getFindings().getFirst().getTitle().isBlank());
        assertTrue(response.getFindings().getFirst().getDetail().contains("Lifecycle"));
    }
}
