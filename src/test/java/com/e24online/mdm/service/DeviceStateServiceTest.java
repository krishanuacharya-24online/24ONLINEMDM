package com.e24online.mdm.service;

import com.e24online.mdm.domain.DeviceInstalledApplication;
import com.e24online.mdm.domain.DeviceSystemSnapshot;
import com.e24online.mdm.domain.DeviceTrustProfile;
import com.e24online.mdm.domain.OsReleaseLifecycleMaster;
import com.e24online.mdm.records.posture.evaluation.LifecycleResolution;
import com.e24online.mdm.records.posture.evaluation.ParsedPosture;
import com.e24online.mdm.repository.DeviceInstalledApplicationRepository;
import com.e24online.mdm.repository.DeviceSystemSnapshotRepository;
import com.e24online.mdm.repository.DeviceTrustProfileRepository;
import com.e24online.mdm.repository.OsReleaseLifecycleMasterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceStateServiceTest {

    @Mock
    private DeviceTrustProfileRepository profileRepository;

    @Mock
    private DeviceSystemSnapshotRepository snapshotRepository;

    @Mock
    private DeviceInstalledApplicationRepository installedApplicationRepository;

    @Mock
    private OsReleaseLifecycleMasterRepository osLifecycleRepository;

    @Mock
    private NamedParameterJdbcTemplate jdbc;

    private ObjectMapper objectMapper;
    private DeviceStateService service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        service = new DeviceStateService(
                profileRepository,
                snapshotRepository,
                installedApplicationRepository,
                osLifecycleRepository,
                jdbc,
                reactor.core.scheduler.Schedulers.immediate(),
                objectMapper
        );
    }

    @Test
    void parsePosture_validPayload_extractsNormalizedValues() {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("os_type", "windows");
        root.put("os_name", "Windows 11");
        root.put("os_version", "11.0.1");
        root.put("device_type", "laptop");
        root.put("api_level", 33);
        root.put("root_detected", false);
        ArrayNode apps = root.putArray("installed_apps");
        apps.addObject().put("app_name", "Chrome").put("app_os_type", "WINDOWS");

        ParsedPosture parsed = service.parsePosture(
                root,
                "dev-1",
                "agent-1",
                "tenant-a",
                OffsetDateTime.now()
        );

        assertEquals("WINDOWS", parsed.osType());
        assertEquals("WINDOWS 11", parsed.osName());
        assertEquals("LAPTOP", parsed.deviceType());
        assertEquals(33, parsed.apiLevel());
        assertEquals(1, parsed.installedApps().size());
    }

    @Test
    void parsePosture_acceptsDynamicOsAndDeviceTypes() {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("os_type", "solaris");
        root.put("os_name", "Solaris 11");
        root.put("os_version", "11.4");
        root.put("device_type", "workstation");

        ParsedPosture parsed = service.parsePosture(
                root,
                "dev-1",
                "agent-1",
                "tenant-a",
                OffsetDateTime.now()
        );

        assertEquals("SOLARIS", parsed.osType());
        assertEquals("SOLARIS 11", parsed.osName());
        assertEquals("WORKSTATION", parsed.deviceType());
    }

    @Test
    void parsePosture_rejectsMissingOsType() {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("os_name", "Windows");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.parsePosture(
                root, "dev-1", "agent-1", "tenant-a", OffsetDateTime.now()
        ));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void parsePosture_rejectsOversizedInstalledApps() {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("os_type", "WINDOWS");
        ArrayNode apps = root.putArray("installed_apps");
        for (int i = 0; i < 5001; i++) {
            apps.addObject().put("app_name", "app-" + i);
        }

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.parsePosture(
                root, "dev-1", "agent-1", "tenant-a", OffsetDateTime.now()
        ));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void upsertTrustProfile_createsNewProfileWhenMissing() {
        ParsedPosture parsed = parsedPostureWithApps("WINDOWS", "LAPTOP", objectMapper.createArrayNode());
        when(profileRepository.findActiveByTenantAndDevice("tenant-a", "dev-1")).thenReturn(Optional.empty());
        when(profileRepository.save(any(DeviceTrustProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DeviceTrustProfile profile = service.upsertTrustProfile("tenant-a", parsed, OffsetDateTime.now());

        assertEquals("tenant-a", profile.getTenantId());
        assertEquals("dev-1", profile.getDeviceExternalId());
        assertEquals("WINDOWS", profile.getOsType());
        assertEquals("LAPTOP", profile.getDeviceType());
        assertEquals("NOT_TRACKED", profile.getOsLifecycleState());
        assertFalse(profile.isDeleted());
    }

    @Test
    void upsertTrustProfile_updatesExistingProfile() {
        DeviceTrustProfile existing = new DeviceTrustProfile();
        existing.setId(10L);
        existing.setTenantId("tenant-a");
        existing.setDeviceExternalId("dev-1");
        existing.setOsLifecycleState("SUPPORTED");
        existing.setLastRecalculatedAt(OffsetDateTime.now().minusDays(1));

        when(profileRepository.findActiveByTenantAndDevice("tenant-a", "dev-1")).thenReturn(Optional.of(existing));
        when(profileRepository.save(existing)).thenReturn(existing);

        ParsedPosture parsed = parsedPostureWithApps("MACOS", "DESKTOP", objectMapper.createArrayNode());
        DeviceTrustProfile saved = service.upsertTrustProfile("tenant-a", parsed, OffsetDateTime.now());

        assertEquals(10L, saved.getId());
        assertEquals("MACOS", saved.getOsType());
        assertEquals("DESKTOP", saved.getDeviceType());
        assertEquals("SUPPORTED", saved.getOsLifecycleState());
    }

    @Test
    void saveSnapshot_insertsWhenNotExisting() {
        when(jdbc.queryForObject(anyString(), any(org.springframework.jdbc.core.namedparam.MapSqlParameterSource.class), eq(Long.class)))
                .thenReturn(0L);
        when(snapshotRepository.save(any(DeviceSystemSnapshot.class))).thenAnswer(invocation -> {
            DeviceSystemSnapshot row = invocation.getArgument(0);
            row.setId(100L);
            return row;
        });

        DeviceTrustProfile profile = new DeviceTrustProfile();
        profile.setId(20L);
        ParsedPosture parsed = parsedPostureWithApps("WINDOWS", "LAPTOP", objectMapper.createArrayNode());

        DeviceSystemSnapshot snapshot = service.saveSnapshot(99L, profile, parsed, OffsetDateTime.now());

        assertNotNull(snapshot);
        assertEquals(100L, snapshot.getId());
        assertEquals(99L, snapshot.getDevicePosturePayloadId());
        assertEquals(20L, snapshot.getDeviceTrustProfileId());
    }

    @Test
    void saveSnapshot_loadsExistingWhenAlreadyPresent() {
        DeviceSystemSnapshot existing = new DeviceSystemSnapshot();
        existing.setId(201L);
        when(jdbc.queryForObject(anyString(), any(org.springframework.jdbc.core.namedparam.MapSqlParameterSource.class), eq(Long.class)))
                .thenReturn(1L);
        when(jdbc.query(anyString(), any(org.springframework.jdbc.core.namedparam.MapSqlParameterSource.class),
                org.mockito.ArgumentMatchers.<RowMapper<Long>>any()))
                .thenReturn(List.of(201L));
        when(snapshotRepository.findById(201L)).thenReturn(Optional.of(existing));

        DeviceTrustProfile profile = new DeviceTrustProfile();
        profile.setId(20L);
        ParsedPosture parsed = parsedPostureWithApps("WINDOWS", "LAPTOP", objectMapper.createArrayNode());

        DeviceSystemSnapshot snapshot = service.saveSnapshot(99L, profile, parsed, OffsetDateTime.now());
        assertEquals(201L, snapshot.getId());
    }

    @Test
    void saveSnapshot_throwsWhenExistingButRecordMissing() {
        when(jdbc.queryForObject(anyString(), any(org.springframework.jdbc.core.namedparam.MapSqlParameterSource.class), eq(Long.class)))
                .thenReturn(1L);
        when(jdbc.query(anyString(), any(org.springframework.jdbc.core.namedparam.MapSqlParameterSource.class),
                org.mockito.ArgumentMatchers.<RowMapper<Long>>any()))
                .thenReturn(List.of());

        DeviceTrustProfile profile = new DeviceTrustProfile();
        profile.setId(20L);
        ParsedPosture parsed = parsedPostureWithApps("WINDOWS", "LAPTOP", objectMapper.createArrayNode());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                service.saveSnapshot(99L, profile, parsed, OffsetDateTime.now()));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void saveInstalledApps_createsAndDeduplicatesEntries() {
        when(jdbc.queryForObject(anyString(), any(org.springframework.jdbc.core.namedparam.MapSqlParameterSource.class), eq(Long.class)))
                .thenReturn(0L);
        when(jdbc.batchUpdate(anyString(), any(org.springframework.jdbc.core.namedparam.MapSqlParameterSource[].class)))
                .thenReturn(new int[] {1, 1, 1});

        ArrayNode apps = objectMapper.createArrayNode();
        apps.addObject()
                .put("app_name", "Chrome")
                .put("app_os_type", "WINDOWS")
                .put("package_id", "com.chrome")
                .put("status", "ACTIVE");
        apps.addObject() // duplicate (same os+package+name)
                .put("app_name", "Chrome")
                .put("app_os_type", "WINDOWS")
                .put("package_id", "com.chrome")
                .put("status", "ACTIVE");
        apps.addObject() // dynamic app os type should still persist
                .put("app_name", "Bad")
                .put("app_os_type", "SOLARIS")
                .put("package_id", "bad.pkg");
        apps.addObject() // invalid status -> defaults ACTIVE
                .put("app_name", "Slack")
                .put("app_os_type", "WINDOWS")
                .put("package_id", "com.slack")
                .put("status", "UNKNOWN_BAD");

        ParsedPosture parsed = parsedPostureWithApps("WINDOWS", "LAPTOP", apps);
        DeviceTrustProfile profile = new DeviceTrustProfile();
        profile.setId(33L);
        DeviceInstalledApplication one = new DeviceInstalledApplication();
        one.setId(1001L);
        one.setStatus("ACTIVE");
        DeviceInstalledApplication two = new DeviceInstalledApplication();
        two.setId(1002L);
        two.setStatus("ACTIVE");
        DeviceInstalledApplication three = new DeviceInstalledApplication();
        three.setId(1003L);
        three.setStatus("ACTIVE");
        when(installedApplicationRepository.findByPayloadId(101L)).thenReturn(List.of(one, two, three));

        List<DeviceInstalledApplication> saved = service.saveInstalledApps(101L, profile, parsed, OffsetDateTime.now());

        assertEquals(3, saved.size());
        assertEquals("ACTIVE", saved.getFirst().getStatus());

        ArgumentCaptor<org.springframework.jdbc.core.namedparam.MapSqlParameterSource[]> captor =
                ArgumentCaptor.forClass(org.springframework.jdbc.core.namedparam.MapSqlParameterSource[].class);
        verify(jdbc).batchUpdate(anyString(), captor.capture());
        org.springframework.jdbc.core.namedparam.MapSqlParameterSource[] params = captor.getValue();
        assertEquals(3, params.length);
        assertEquals("SOLARIS", params[1].getValue("appOsType"));
        assertEquals("ACTIVE", params[2].getValue("status"));
    }

    @Test
    void saveInstalledApps_returnsExistingRowsWhenAlreadyPresent() {
        DeviceInstalledApplication existing = new DeviceInstalledApplication();
        existing.setId(77L);
        when(jdbc.queryForObject(anyString(), any(org.springframework.jdbc.core.namedparam.MapSqlParameterSource.class), eq(Long.class)))
                .thenReturn(1L);
        when(installedApplicationRepository.findByPayloadId(101L)).thenReturn(List.of(existing));

        ParsedPosture parsed = parsedPostureWithApps("WINDOWS", "LAPTOP", objectMapper.createArrayNode());
        DeviceTrustProfile profile = new DeviceTrustProfile();
        profile.setId(33L);

        List<DeviceInstalledApplication> saved = service.saveInstalledApps(101L, profile, parsed, OffsetDateTime.now());
        assertEquals(1, saved.size());
        assertEquals(77L, saved.get(0).getId());
    }

    @Test
    void saveInstalledApps_repairsCorruptedAppNameWithPackageIdFallback() {
        when(jdbc.queryForObject(anyString(), any(org.springframework.jdbc.core.namedparam.MapSqlParameterSource.class), eq(Long.class)))
                .thenReturn(0L);
        when(jdbc.batchUpdate(anyString(), any(org.springframework.jdbc.core.namedparam.MapSqlParameterSource[].class)))
                .thenReturn(new int[] {1});

        ArrayNode apps = objectMapper.createArrayNode();
        apps.addObject()
                .put("app_name", "\uFFFDTorrent")
                .put("app_os_type", "WINDOWS")
                .put("package_id", "uTorrent")
                .put("publisher", "BitTorrent Limited");

        ParsedPosture parsed = parsedPostureWithApps("WINDOWS", "LAPTOP", apps);
        DeviceTrustProfile profile = new DeviceTrustProfile();
        profile.setId(33L);

        DeviceInstalledApplication existing = new DeviceInstalledApplication();
        existing.setId(99L);
        existing.setAppName("uTorrent");
        when(installedApplicationRepository.findByPayloadId(101L)).thenReturn(List.of(existing));

        service.saveInstalledApps(101L, profile, parsed, OffsetDateTime.now());

        ArgumentCaptor<org.springframework.jdbc.core.namedparam.MapSqlParameterSource[]> captor =
                ArgumentCaptor.forClass(org.springframework.jdbc.core.namedparam.MapSqlParameterSource[].class);
        verify(jdbc).batchUpdate(anyString(), captor.capture());
        org.springframework.jdbc.core.namedparam.MapSqlParameterSource[] params = captor.getValue();
        assertEquals(1, params.length);
        assertEquals("uTorrent", params[0].getValue("appName"));
        assertEquals("uTorrent", params[0].getValue("packageId"));
    }

    @Test
    void resolveLifecycle_handlesSupportedEolEeolAndNotTracked() {
        OsReleaseLifecycleMaster row = new OsReleaseLifecycleMaster();
        row.setId(5L);
        row.setOsType("WINDOWS");
        row.setOsName("WINDOWS 11");
        row.setCycle("11");
        row.setStatus("ACTIVE");
        row.setDeleted(false);
        row.setSupportState("SUPPORTED");
        row.setEolOn(LocalDate.now().plusDays(10));
        row.setEeolOn(LocalDate.now().plusDays(20));
        when(osLifecycleRepository.findAll()).thenReturn(List.of(row));

        ParsedPosture parsed = parsedPostureWithApps("WINDOWS", "LAPTOP", objectMapper.createArrayNode());
        LifecycleResolution supported = service.resolveLifecycle(parsed, LocalDate.now());
        assertEquals("SUPPORTED", supported.state());

        row.setEolOn(LocalDate.now().minusDays(1));
        row.setEeolOn(LocalDate.now().plusDays(1));
        LifecycleResolution eol = service.resolveLifecycle(parsed, LocalDate.now());
        assertEquals("EOL", eol.state());

        row.setEeolOn(LocalDate.now().minusDays(1));
        LifecycleResolution eeol = service.resolveLifecycle(parsed, LocalDate.now());
        assertEquals("EEOL", eeol.state());

        row.setSupportState("NOT_FOUND");
        row.setEolOn(LocalDate.now().plusDays(100));
        row.setEeolOn(LocalDate.now().plusDays(200));
        LifecycleResolution notTracked = service.resolveLifecycle(parsed, LocalDate.now());
        assertEquals("NOT_TRACKED", notTracked.state());
    }

    @Test
    void resolveLifecycle_returnsNotTrackedWhenNoCycle() {
        ParsedPosture parsed = parsedPostureWithApps("WINDOWS", "LAPTOP", objectMapper.createArrayNode());
        ParsedPosture noVersion = new ParsedPosture(
                parsed.tenantId(),
                parsed.deviceExternalId(),
                parsed.agentId(),
                parsed.osType(),
                parsed.osName(),
                null,
                null,
                parsed.deviceType(),
                parsed.timeZone(),
                parsed.kernelVersion(),
                parsed.apiLevel(),
                parsed.osBuildNumber(),
                parsed.manufacturer(),
                parsed.rootDetected(),
                parsed.runningOnEmulator(),
                parsed.usbDebuggingStatus(),
                parsed.captureTime(),
                parsed.root(),
                parsed.installedApps()
        );

        LifecycleResolution lifecycle = service.resolveLifecycle(noVersion, LocalDate.now());
        assertEquals("NOT_TRACKED", lifecycle.state());
        assertNull(lifecycle.masterId());
    }

    @Test
    void applyLifecycle_updatesProfileAndSnapshot() {
        DeviceTrustProfile profile = new DeviceTrustProfile();
        DeviceSystemSnapshot snapshot = new DeviceSystemSnapshot();

        service.applyLifecycle(profile, snapshot, new LifecycleResolution(9L, "EOL", "OS_EOL"));

        assertEquals("EOL", profile.getOsLifecycleState());
        assertEquals(9L, profile.getOsReleaseLifecycleMasterId());
        assertEquals(9L, snapshot.getOsReleaseLifecycleMasterId());
        verifyNoUnexpectedNull(snapshot);
    }

    private ParsedPosture parsedPostureWithApps(String osType, String deviceType, ArrayNode apps) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("os_type", osType);
        root.put("os_name", "WINDOWS 11");
        root.put("os_version", "11.0.1");
        root.put("capture_time", OffsetDateTime.now().toString());
        root.put("device_type", deviceType);
        root.put("api_level", 33);
        root.put("manufacturer", "Acme");
        root.set("installed_apps", apps);

        return new ParsedPosture(
                "tenant-a",
                "dev-1",
                "agent-1",
                osType,
                "WINDOWS 11",
                "11.0.1",
                "11",
                deviceType,
                "UTC",
                "1.0",
                33,
                "19045",
                "Acme",
                false,
                false,
                false,
                OffsetDateTime.now(),
                root,
                apps
        );
    }

    private void verifyNoUnexpectedNull(DeviceSystemSnapshot snapshot) {
        assertNotNull(snapshot);
    }
}
