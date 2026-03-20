package com.e24online.mdm.web;

import com.e24online.mdm.domain.DeviceDecisionResponse;
import com.e24online.mdm.domain.DeviceInstalledApplication;
import com.e24online.mdm.domain.DevicePosturePayload;
import com.e24online.mdm.domain.DeviceSystemSnapshot;
import com.e24online.mdm.domain.DeviceTrustProfile;
import com.e24online.mdm.domain.DeviceTrustScoreEvent;
import com.e24online.mdm.domain.PostureEvaluationRun;
import com.e24online.mdm.records.user.UserPrincipal;
import com.e24online.mdm.repository.DeviceDecisionResponseRepository;
import com.e24online.mdm.repository.DeviceEnrollmentRepository;
import com.e24online.mdm.repository.DeviceInstalledApplicationRepository;
import com.e24online.mdm.repository.DevicePosturePayloadRepository;
import com.e24online.mdm.repository.DeviceSystemSnapshotRepository;
import com.e24online.mdm.repository.DeviceTrustProfileRepository;
import com.e24online.mdm.repository.DeviceTrustScoreEventRepository;
import com.e24online.mdm.repository.PostureEvaluationRunRepository;
import com.e24online.mdm.service.BlockingDb;
import com.e24online.mdm.web.security.AuthenticatedRequestContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DevicesControllerTest {

    @Mock
    private DeviceTrustProfileRepository profileRepository;

    @Mock
    private DeviceTrustScoreEventRepository scoreEventRepository;

    @Mock
    private DeviceSystemSnapshotRepository systemSnapshotRepository;

    @Mock
    private DeviceInstalledApplicationRepository installedApplicationRepository;

    @Mock
    private DeviceDecisionResponseRepository decisionResponseRepository;

    @Mock
    private DevicePosturePayloadRepository payloadRepository;

    @Mock
    private PostureEvaluationRunRepository runRepository;

    @Mock
    private DeviceEnrollmentRepository enrollmentRepository;

    @Mock
    private AuthenticatedRequestContext requestContext;

    @Mock
    private Authentication authentication;

    private DevicesController controller;

    @BeforeEach
    void setUp() {
        controller = new DevicesController(
                profileRepository,
                scoreEventRepository,
                systemSnapshotRepository,
                installedApplicationRepository,
                decisionResponseRepository,
                payloadRepository,
                runRepository,
                enrollmentRepository,
                new BlockingDb(Schedulers.immediate()),
                requestContext
        );
        ReflectionTestUtils.setField(controller, "defaultPage", 0);
        ReflectionTestUtils.setField(controller, "defaultSize", 50);
        ReflectionTestUtils.setField(controller, "maxSize", 500);
        ReflectionTestUtils.setField(controller, "maxPage", 1000);
    }

    @Test
    void listTrustProfiles_normalizesFiltersAndDelegates() {
        when(requestContext.requireUserPrincipal(authentication))
                .thenReturn(new UserPrincipal(10L, "admin", "TENANT_ADMIN", 1L));
        when(requestContext.resolveTenantId(authentication, "tenant-a"))
                .thenReturn(Mono.just("tenant-a"));
        when(profileRepository.findPaged("tenant-a", "dev-1", "WINDOWS", "Win11", "HIGH_RISK", null, 25, 0))
                .thenReturn(List.of(new DeviceTrustProfile()));

        List<DeviceTrustProfile> response = controller
                .listTrustProfiles("tenant-a", authentication, " dev-1 ", "windows", " Win11 ", "high_risk", 0, 25)
                .collectList()
                .block();

        assertNotNull(response);
        assertEquals(1, response.size());
        verify(profileRepository).findPaged("tenant-a", "dev-1", "WINDOWS", "Win11", "HIGH_RISK", null, 25, 0);
    }

    @Test
    void listTrustProfiles_invalidOsType_throwsBadRequest() {
        when(requestContext.requireUserPrincipal(authentication))
                .thenReturn(new UserPrincipal(10L, "admin", "TENANT_ADMIN", 1L));
        when(requestContext.resolveTenantId(authentication, null))
                .thenReturn(Mono.just("tenant-a"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> controller
                .listTrustProfiles(null, authentication, null, "plan9", null, null, 0, 25)
                .collectList()
                .block());

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void getTrustProfile_notFound_returns404() {
        when(requestContext.requireUserPrincipal(authentication))
                .thenReturn(new UserPrincipal(10L, "admin", "TENANT_ADMIN", 1L));
        when(requestContext.resolveTenantId(authentication, null))
                .thenReturn(Mono.just("tenant-a"));
        when(profileRepository.findByIdAndTenant(99L, "tenant-a")).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> controller
                .getTrustProfile(null, authentication, 99L)
                .block());

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void getTrustProfile_tenantUserOutOfScope_returnsForbidden() {
        DeviceTrustProfile profile = new DeviceTrustProfile();
        profile.setId(1L);
        profile.setDeviceExternalId("dev-1");

        when(requestContext.requireUserPrincipal(authentication))
                .thenReturn(new UserPrincipal(77L, "user", "TENANT_USER", 1L));
        when(requestContext.resolveTenantId(authentication, null))
                .thenReturn(Mono.just("tenant-a"));
        when(profileRepository.findByIdAndTenant(1L, "tenant-a")).thenReturn(Optional.of(profile));
        when(enrollmentRepository.countActiveByTenantAndEnrollmentNoAndOwnerUserId("tenant-a", "dev-1", 77L))
                .thenReturn(0L);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> controller
                .getTrustProfile(null, authentication, 1L)
                .block());

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void getLatestSnapshot_notFound_returns404() {
        when(requestContext.requireUserPrincipal(authentication))
                .thenReturn(new UserPrincipal(10L, "admin", "TENANT_ADMIN", 1L));
        when(requestContext.resolveTenantId(authentication, null))
                .thenReturn(Mono.just("tenant-a"));
        when(systemSnapshotRepository.findLatestByDevice("tenant-a", "dev-1")).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> controller
                .getLatestSnapshot(null, authentication, "dev-1")
                .block());

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void getDeviceInstalledApps_invalidStatus_throwsBadRequest() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> controller
                .getDeviceInstalledApps(null, authentication, "dev-1", "BAD", 0, 25)
                .collectList()
                .block());

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void deviceCollectionEndpoints_delegateToRepositories() {
        when(requestContext.requireUserPrincipal(authentication))
                .thenReturn(new UserPrincipal(10L, "admin", "TENANT_ADMIN", 1L));
        when(requestContext.resolveTenantId(authentication, null))
                .thenReturn(Mono.just("tenant-a"));
        when(scoreEventRepository.findByDevice("tenant-a", "dev-1", 25, 0))
                .thenReturn(List.of(new DeviceTrustScoreEvent()));
        when(decisionResponseRepository.findByDevice("tenant-a", "dev-1", 25, 0))
                .thenReturn(List.of(new DeviceDecisionResponse()));
        when(systemSnapshotRepository.findByDevice("tenant-a", "dev-1", 25, 0))
                .thenReturn(List.of(new DeviceSystemSnapshot()));
        when(installedApplicationRepository.findLatestAppsByDevice("tenant-a", "dev-1", 25, 0))
                .thenReturn(List.of(new DeviceInstalledApplication()));
        when(installedApplicationRepository.findAppsByDevice("tenant-a", "dev-1", "ACTIVE", 25, 0))
                .thenReturn(List.of(new DeviceInstalledApplication()));
        when(payloadRepository.findByDevice("tenant-a", "dev-1", 25, 0))
                .thenReturn(List.of(new DevicePosturePayload()));
        when(runRepository.findByDevice("tenant-a", "dev-1", 25, 0))
                .thenReturn(List.of(new PostureEvaluationRun()));

        assertEquals(1, controller.getTrustScoreEvents(null, authentication, "dev-1", 0, 25).collectList().block().size());
        assertEquals(1, controller.getDeviceDecisions(null, authentication, "dev-1", 0, 25).collectList().block().size());
        assertEquals(1, controller.getDeviceSnapshots(null, authentication, "dev-1", 0, 25).collectList().block().size());
        assertEquals(1, controller.getLatestInstalledApps(null, authentication, "dev-1", 0, 25).collectList().block().size());
        assertEquals(1, controller.getDeviceInstalledApps(null, authentication, "dev-1", "ACTIVE", 0, 25).collectList().block().size());
        assertEquals(1, controller.getDevicePosturePayloads(null, authentication, "dev-1", 0, 25).collectList().block().size());
        assertEquals(1, controller.getDeviceEvaluationRuns(null, authentication, "dev-1", 0, 25).collectList().block().size());
    }

    @Test
    void deviceCollectionEndpoints_sanitizeCorruptedAppNamesAndPayloadJson() {
        when(requestContext.requireUserPrincipal(authentication))
                .thenReturn(new UserPrincipal(10L, "admin", "TENANT_ADMIN", 1L));
        when(requestContext.resolveTenantId(authentication, null))
                .thenReturn(Mono.just("tenant-a"));

        DeviceInstalledApplication app = new DeviceInstalledApplication();
        app.setAppName("\uFFFDTorrent");
        app.setPackageId("uTorrent");
        when(installedApplicationRepository.findLatestAppsByDevice("tenant-a", "dev-1", 25, 0))
                .thenReturn(List.of(app));

        DevicePosturePayload payload = new DevicePosturePayload();
        payload.setPayloadJson("{\"installed_apps\":[{\"app_name\":\"\uFFFDTorrent\",\"package_id\":\"uTorrent\"}]}");
        when(payloadRepository.findByDevice("tenant-a", "dev-1", 25, 0))
                .thenReturn(List.of(payload));

        List<DeviceInstalledApplication> apps = controller
                .getLatestInstalledApps(null, authentication, "dev-1", 0, 25)
                .collectList()
                .block();
        List<DevicePosturePayload> payloads = controller
                .getDevicePosturePayloads(null, authentication, "dev-1", 0, 25)
                .collectList()
                .block();

        assertNotNull(apps);
        assertNotNull(payloads);
        assertEquals("uTorrent", apps.getFirst().getAppName());
        assertEquals("{\"installed_apps\":[{\"app_name\":\"uTorrent\",\"package_id\":\"uTorrent\"}]}", payloads.getFirst().getPayloadJson());
    }
}
