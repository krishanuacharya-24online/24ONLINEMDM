package com.e24online.mdm.service;

import com.e24online.mdm.domain.DeviceInstalledApplication;
import com.e24online.mdm.domain.DeviceSystemSnapshot;
import com.e24online.mdm.domain.DeviceTrustProfile;
import com.e24online.mdm.domain.OsReleaseLifecycleMaster;
import com.e24online.mdm.records.cache.CachedLifecycleRows;
import com.e24online.mdm.records.posture.evaluation.LifecycleResolution;
import com.e24online.mdm.records.posture.evaluation.ParsedPosture;
import com.e24online.mdm.repository.DeviceInstalledApplicationRepository;
import com.e24online.mdm.repository.DeviceSystemSnapshotRepository;
import com.e24online.mdm.repository.DeviceTrustProfileRepository;
import com.e24online.mdm.repository.OsReleaseLifecycleMasterRepository;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.scheduler.Scheduler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import static com.e24online.mdm.utils.AgentWorkflowValueUtils.*;

/**
 * Service for managing device state including trust profiles, system snapshots,
 * and installed applications.
 */
@Service
public class DeviceStateService {

    private static final Logger log = LoggerFactory.getLogger(DeviceStateService.class);
    private static final int MAX_TEXT_LENGTH = 255;
    private static final int MAX_VERSION_LENGTH = 128;
    private static final int MAX_OS_CYCLE_LENGTH = 64;
    private static final int MAX_TIME_ZONE_LENGTH = 100;
    private static final int MAX_INSTALLED_APPS = 5000;
    private static final Set<String> OS_TYPES = Set.of("ANDROID", "IOS", "WINDOWS", "MACOS", "LINUX", "CHROMEOS", "FREEBSD", "OPENBSD");
    private static final Set<String> APP_OS_TYPES = Set.of("ANDROID", "IOS", "WINDOWS", "MACOS", "LINUX");
    private static final long DEFAULT_LIFECYCLE_CACHE_SECONDS = 60L;

    private final DeviceTrustProfileRepository profileRepository;
    private final DeviceSystemSnapshotRepository snapshotRepository;
    private final DeviceInstalledApplicationRepository installedApplicationRepository;
    private final OsReleaseLifecycleMasterRepository osLifecycleRepository;
    private final NamedParameterJdbcTemplate jdbc;
    private final Scheduler jdbcScheduler;
    private final ObjectMapper objectMapper;
    private volatile CachedLifecycleRows lifecycleRowsCache;

    @Value("${mdm.lifecycle.cache-seconds:60}")
    private long lifecycleCacheSeconds = DEFAULT_LIFECYCLE_CACHE_SECONDS;

    public DeviceStateService(DeviceTrustProfileRepository profileRepository,
                              DeviceSystemSnapshotRepository snapshotRepository,
                              DeviceInstalledApplicationRepository installedApplicationRepository,
                              OsReleaseLifecycleMasterRepository osLifecycleRepository,
                              NamedParameterJdbcTemplate jdbc,
                              Scheduler jdbcScheduler,
                              ObjectMapper objectMapper) {
        this.profileRepository = profileRepository;
        this.snapshotRepository = snapshotRepository;
        this.installedApplicationRepository = installedApplicationRepository;
        this.osLifecycleRepository = osLifecycleRepository;
        this.jdbc = jdbc;
        this.jdbcScheduler = jdbcScheduler;
        this.objectMapper = objectMapper;
    }

    /**
     * Parse and validate posture data from JSON payload.
     */
    public ParsedPosture parsePosture(JsonNode root,
                                      String deviceExternalId,
                                      String agentId,
                                      String tenantId,
                                      OffsetDateTime now) {
        log.debug("parsePosture method");
        String osType = normalizeUpper(text(root, "os_type"));
        if (osType == null || !OS_TYPES.contains(osType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "payload_json.os_type is required");
        }

        String osName = normalizeUpper(trimToNullAndCap(text(root, "os_name"), MAX_TEXT_LENGTH));
        String osVersion = trimToNullAndCap(text(root, "os_version"), MAX_VERSION_LENGTH);
        String osCycle = trimToNullAndCap(text(root, "os_cycle"), MAX_OS_CYCLE_LENGTH);
        String deviceType = normalizeUpper(trimToNullAndCap(text(root, "device_type"), MAX_TEXT_LENGTH));
        String timeZone = trimToNullAndCap(text(root, "time_zone"), MAX_TIME_ZONE_LENGTH);
        String kernelVersion = trimToNullAndCap(text(root, "kernel_version"), MAX_VERSION_LENGTH);
        Integer apiLevel = intValue(root.get("api_level"));
        String osBuildNumber = trimToNullAndCap(text(root, "os_build_number"), MAX_VERSION_LENGTH);
        String manufacturer = trimToNullAndCap(text(root, "manufacturer"), MAX_TEXT_LENGTH);
        Boolean rootDetected = boolValue(root.get("root_detected"));
        Boolean emulator = boolValue(root.get("running_on_emulator"));
        Boolean usbDebugging = boolValue(root.get("usb_debugging_status"));
        OffsetDateTime captureTime = parseOffsetDateTime(text(root, "capture_time")).orElse(now);

        JsonNode appsNode = root.get("installed_apps");
        if (appsNode != null && appsNode.isArray() && appsNode.size() > MAX_INSTALLED_APPS) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "payload_json.installed_apps exceeds max allowed size of " + MAX_INSTALLED_APPS
            );
        }

        return new ParsedPosture(
                tenantId,
                deviceExternalId,
                agentId,
                osType,
                osName,
                osVersion,
                osCycle,
                deviceType,
                timeZone,
                kernelVersion,
                apiLevel,
                osBuildNumber,
                manufacturer,
                rootDetected,
                emulator,
                usbDebugging,
                captureTime,
                root,
                appsNode != null && appsNode.isArray() ? appsNode : objectMapper.createObjectNode()
        );
    }

    /**
     * Create or update device trust profile.
     */
    @Transactional
    public DeviceTrustProfile upsertTrustProfile(String tenantId, ParsedPosture parsed, OffsetDateTime now) {
        DeviceTrustProfile profile = profileRepository.findActiveByTenantAndDevice(tenantId, parsed.deviceExternalId()).orElse(null);
        if (profile == null) {
            profile = new DeviceTrustProfile();
            profile.setTenantId(tenantId);
            profile.setDeviceExternalId(parsed.deviceExternalId());
            profile.setCurrentScore((short) 100);
            profile.setScoreBand("TRUSTED");
            profile.setPostureStatus("COMPLIANT");
            profile.setDeleted(false);
            profile.setCreatedAt(now);
            profile.setCreatedBy("posture-parser");
        }

        profile.setDeviceType(validDeviceType(parsed.deviceType()));
        profile.setOsType(parsed.osType());
        profile.setOsName(parsed.osName());
        if (profile.getOsLifecycleState() == null || profile.getOsLifecycleState().isBlank()) {
            profile.setOsLifecycleState("NOT_TRACKED");
        }
        if (profile.getLastRecalculatedAt() == null) {
            profile.setLastRecalculatedAt(now);
        }
        profile.setModifiedAt(now);
        profile.setModifiedBy("posture-parser");

        return profileRepository.save(profile);
    }

    /**
     * Save device system snapshot.
     */
    @Transactional
    public DeviceSystemSnapshot saveSnapshot(Long payloadId, DeviceTrustProfile profile, ParsedPosture parsed, OffsetDateTime now) {
        if (!snapshotExists(payloadId)) {
            clearLatestSnapshot(profile.getId());

            DeviceSystemSnapshot snapshot = new DeviceSystemSnapshot();
            snapshot.setDevicePosturePayloadId(payloadId);
            snapshot.setDeviceTrustProfileId(profile.getId());
            snapshot.setCaptureTime(parsed.captureTime());
            snapshot.setDeviceType(validDeviceType(parsed.deviceType()));
            snapshot.setOsType(parsed.osType());
            snapshot.setOsName(parsed.osName());
            snapshot.setOsCycle(parsed.osCycle());
            snapshot.setOsVersion(parsed.osVersion());
            snapshot.setTimeZone(parsed.timeZone());
            snapshot.setKernelVersion(parsed.kernelVersion());
            snapshot.setApiLevel(parsed.apiLevel());
            snapshot.setOsBuildNumber(parsed.osBuildNumber());
            snapshot.setManufacturer(parsed.manufacturer());
            snapshot.setRootDetected(parsed.rootDetected());
            snapshot.setRunningOnEmulator(parsed.runningOnEmulator());
            snapshot.setUsbDebuggingStatus(parsed.usbDebuggingStatus());
            snapshot.setLatest(true);

            return snapshotRepository.save(snapshot);
        }
        return loadSnapshotByPayload(payloadId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.BAD_REQUEST, "Snapshot not found for existing payload"));
    }

    /**
     * Save installed applications.
     */
    @Transactional
    public List<DeviceInstalledApplication> saveInstalledApps(Long payloadId, DeviceTrustProfile profile,
                                                              ParsedPosture parsed, OffsetDateTime now) {
        if (installedAppsExist(payloadId)) {
            return loadInstalledAppsByPayload(payloadId);
        }

        List<MapSqlParameterSource> batchParams = new ArrayList<>();
        Set<String> dedupe = new HashSet<>();

        for (JsonNode appNode : parsed.installedApps()) {
            String appName = trimToNullAndCap(text(appNode, "app_name"), MAX_TEXT_LENGTH);
            if (appName == null) {
                continue;
            }

            String appOsType = normalizeUpper(firstNonBlank(
                    trimToNullAndCap(text(appNode, "app_os_type"), MAX_TEXT_LENGTH),
                    trimToNullAndCap(text(appNode, "os_type"), MAX_TEXT_LENGTH),
                    parsed.osType()
            ));
            if (appOsType == null || !APP_OS_TYPES.contains(appOsType)) {
                continue;
            }

            String packageId = trimToNullAndCap(text(appNode, "package_id"), MAX_TEXT_LENGTH);
            String dedupeKey = appOsType + "::" + (packageId == null ? "" : packageId.toLowerCase(Locale.ROOT)) + "::" + appName.toLowerCase(Locale.ROOT);
            if (!dedupe.add(dedupeKey)) {
                continue;
            }

            String status = normalizeUpper(firstNonBlank(trimToNullAndCap(text(appNode, "status"), MAX_TEXT_LENGTH), "ACTIVE"));
            if (!Set.of("ACTIVE", "REMOVED", "UNKNOWN").contains(status)) {
                status = "ACTIVE";
            }

            MapSqlParameterSource params = new MapSqlParameterSource()
                    .addValue("payloadId", payloadId)
                    .addValue("profileId", profile.getId())
                    .addValue("captureTime", parsed.captureTime())
                    .addValue("appName", appName)
                    .addValue("publisher", trimToNullAndCap(text(appNode, "publisher"), MAX_TEXT_LENGTH))
                    .addValue("packageId", packageId)
                    .addValue("appOsType", appOsType)
                    .addValue("appVersion", trimToNullAndCap(text(appNode, "app_version"), MAX_VERSION_LENGTH))
                    .addValue("latestAvailableVersion", trimToNullAndCap(text(appNode, "latest_available_version"), MAX_VERSION_LENGTH))
                    .addValue("systemApp", boolValue(appNode.get("is_system_app")))
                    .addValue("installSource", trimToNullAndCap(text(appNode, "install_source"), MAX_TEXT_LENGTH))
                    .addValue("status", status)
                    .addValue("createdAt", now)
                    .addValue("createdBy", "posture-parser");
            batchParams.add(params);
        }

        if (batchParams.isEmpty()) {
            return List.of();
        }

        String insertSql = """
                INSERT INTO device_installed_application (
                    device_posture_payload_id,
                    device_trust_profile_id,
                    capture_time,
                    app_name,
                    publisher,
                    package_id,
                    app_os_type,
                    app_version,
                    latest_available_version,
                    is_system_app,
                    install_source,
                    status,
                    created_at,
                    created_by
                ) VALUES (
                    :payloadId,
                    :profileId,
                    :captureTime,
                    :appName,
                    :publisher,
                    :packageId,
                    :appOsType,
                    :appVersion,
                    :latestAvailableVersion,
                    :systemApp,
                    :installSource,
                    :status,
                    :createdAt,
                    :createdBy
                )
                ON CONFLICT DO NOTHING
                """;

        jdbc.batchUpdate(insertSql, batchParams.toArray(MapSqlParameterSource[]::new));
        return loadInstalledAppsByPayload(payloadId);
    }

    /**
     * Resolve OS lifecycle state.
     */
    public LifecycleResolution resolveLifecycle(ParsedPosture parsed, LocalDate today) {
        String normalizedOsName = normalizeUpper(parsed.osName());
        String cycle = trimToNull(parsed.osCycle());
        if (cycle == null) {
            cycle = deriveCycle(parsed.osVersion());
        }
        if (cycle == null) {
            return new LifecycleResolution(null, "NOT_TRACKED", "OS_NOT_TRACKED");
        }

        final String cycleKey = cycle;
        List<OsReleaseLifecycleMaster> all = loadLifecycleRows();

        Optional<OsReleaseLifecycleMaster> match = all.stream()
                .filter(x -> !x.isDeleted())
                .filter(x -> "ACTIVE".equalsIgnoreCase(x.getStatus()))
                .filter(x -> equalsIgnoreCase(x.getOsType(), parsed.osType()))
                .filter(x -> {
                    if (x.getOsName() == null || x.getOsName().isBlank()) {
                        return true;
                    }
                    return equalsIgnoreCase(x.getOsName(), normalizedOsName);
                })
                .filter(x -> equalsIgnoreCase(x.getCycle(), cycleKey))
                .findFirst();

        if (match.isEmpty()) {
            return new LifecycleResolution(null, "NOT_TRACKED", "OS_NOT_TRACKED");
        }

        OsReleaseLifecycleMaster row = match.get();
        String state = "SUPPORTED";
        if ("NOT_FOUND".equalsIgnoreCase(row.getSupportState())) {
            state = "NOT_TRACKED";
        } else if (row.getEeolOn() != null && today.isAfter(row.getEeolOn())) {
            state = "EEOL";
        } else if (row.getEolOn() != null && today.isAfter(row.getEolOn())) {
            state = "EOL";
        }

        return new LifecycleResolution(row.getId(), state, lifecycleSignalFor(state));
    }

    /**
     * Apply lifecycle state to profile and snapshot.
     */
    public void applyLifecycle(DeviceTrustProfile profile, DeviceSystemSnapshot snapshot, LifecycleResolution lifecycle) {
        profile.setOsLifecycleState(lifecycle.state());
        profile.setOsReleaseLifecycleMasterId(lifecycle.masterId());
        snapshot.setOsReleaseLifecycleMasterId(lifecycle.masterId());
    }

    private Optional<OffsetDateTime> parseOffsetDateTime(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(OffsetDateTime.parse(value.trim()));
        } catch (Exception _) {
            return Optional.empty();
        }
    }

    private String deriveCycle(String osVersion) {
        String v = trimToNull(osVersion);
        if (v == null) {
            return null;
        }
        String major = v.split("[^A-Za-z0-9]+")[0];
        return trimToNull(major);
    }

    private String lifecycleSignalFor(String state) {
        return switch (normalizeUpper(state)) {
            case "EEOL" -> "OS_EEOL";
            case "EOL" -> "OS_EOL";
            case "NOT_TRACKED" -> "OS_NOT_TRACKED";
            case null -> "-";
            default -> "OS_SUPPORTED";
        };
    }

    private String validDeviceType(String value) {
        String normalized = normalizeUpper(value);
        if (normalized == null) {
            return null;
        }
        return Set.of("PHONE", "TABLET", "LAPTOP", "DESKTOP", "IOT", "SERVER").contains(normalized) ? normalized : null;
    }

    private <T> List<T> toList(Iterable<T> iterable) {
        if (iterable == null) {
            return Collections.emptyList();
        }
        List<T> out = new ArrayList<>();
        iterable.forEach(out::add);
        return out;
    }

    private List<OsReleaseLifecycleMaster> loadLifecycleRows() {
        long nowMs = System.currentTimeMillis();
        CachedLifecycleRows cached = lifecycleRowsCache;
        if (cached != null && cached.expiresAtEpochMillis() > nowMs) {
            return cached.rows();
        }
        List<OsReleaseLifecycleMaster> loaded = toList(osLifecycleRepository.findAll());
        long ttlMillis = Math.max(0L, lifecycleCacheSeconds) * 1000L;
        if (ttlMillis <= 0L) {
            lifecycleRowsCache = null;
            return loaded;
        }
        lifecycleRowsCache = new CachedLifecycleRows(nowMs + ttlMillis, loaded);
        return loaded;
    }

    private boolean snapshotExists(Long payloadId) {
        String sql = "SELECT COUNT(*) FROM device_system_snapshot WHERE device_posture_payload_id = :payloadId";
        Long count = jdbc.queryForObject(sql, new MapSqlParameterSource("payloadId", payloadId), Long.class);
        return count != null && count > 0;
    }

    private boolean installedAppsExist(Long payloadId) {
        String sql = "SELECT COUNT(*) FROM device_installed_application WHERE device_posture_payload_id = :payloadId";
        Long count = jdbc.queryForObject(sql, new MapSqlParameterSource("payloadId", payloadId), Long.class);
        return count != null && count > 0;
    }

    private void clearLatestSnapshot(Long profileId) {
        jdbc.update(
                "UPDATE device_system_snapshot SET is_latest = false WHERE device_trust_profile_id = :profileId AND is_latest = true",
                new MapSqlParameterSource("profileId", profileId)
        );
    }

    private Optional<DeviceSystemSnapshot> loadSnapshotByPayload(Long payloadId) {
        String sql = "SELECT id FROM device_system_snapshot WHERE device_posture_payload_id = :payloadId LIMIT 1";
        List<Long> ids = jdbc.query(sql, new MapSqlParameterSource("payloadId", payloadId), (rs, _) -> rs.getLong(1));
        if (ids.isEmpty()) {
            return Optional.empty();
        }
        return snapshotRepository.findById(ids.getFirst());
    }

    private List<DeviceInstalledApplication> loadInstalledAppsByPayload(Long payloadId) {
        return installedApplicationRepository.findByPayloadId(payloadId);
    }

}
