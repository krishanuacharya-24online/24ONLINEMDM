package com.e24online.mdm.web;

import com.e24online.mdm.domain.*;
import com.e24online.mdm.records.PageParams;
import com.e24online.mdm.records.Role;
import com.e24online.mdm.records.tenant.TenantContext;
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
import com.e24online.mdm.records.user.UserPrincipal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Set;

/**
 * REST controller for device-related endpoints.
 * Provides access to device trust profiles, score events, snapshots, and evaluation data.
 */
@RestController
@RequestMapping("${api.version.prefix:v1}")
@PreAuthorize("hasAnyRole('PRODUCT_ADMIN','TENANT_ADMIN','TENANT_USER')")
public class DevicesController {

    @Value("${api.pagination.default-page:0}")
    private int defaultPage;

    @Value("${api.pagination.default-size:50}")
    private int defaultSize;

    @Value("${api.pagination.max-size:500}")
    private int maxSize;

    @Value("${api.pagination.max-page:1000}")
    private int maxPage;

    private static final Set<String> VALID_OS_TYPES = Set.of("WINDOWS", "MACOS", "LINUX", "ANDROID", "IOS");
    private static final Set<String> VALID_SCORE_BANDS = Set.of(
            "TRUSTED", "LOW_RISK", "MEDIUM_RISK", "HIGH_RISK", "CRITICAL", "UNKNOWN"
    );
    private static final Set<String> VALID_APP_STATUSES = Set.of("ACTIVE", "REMOVED", "UNKNOWN");

    private final DeviceTrustProfileRepository profileRepository;
    private final DeviceTrustScoreEventRepository scoreEventRepository;
    private final DeviceSystemSnapshotRepository systemSnapshotRepository;
    private final DeviceInstalledApplicationRepository installedApplicationRepository;
    private final DeviceDecisionResponseRepository decisionResponseRepository;
    private final DevicePosturePayloadRepository payloadRepository;
    private final PostureEvaluationRunRepository runRepository;
    private final DeviceEnrollmentRepository enrollmentRepository;
    private final BlockingDb blockingDb;
    private final AuthenticatedRequestContext requestContext;

    public DevicesController(DeviceTrustProfileRepository profileRepository,
                             DeviceTrustScoreEventRepository scoreEventRepository,
                             DeviceSystemSnapshotRepository systemSnapshotRepository,
                             DeviceInstalledApplicationRepository installedApplicationRepository,
                             DeviceDecisionResponseRepository decisionResponseRepository,
                             DevicePosturePayloadRepository payloadRepository,
                             PostureEvaluationRunRepository runRepository,
                             DeviceEnrollmentRepository enrollmentRepository,
                             BlockingDb blockingDb,
                             AuthenticatedRequestContext requestContext) {
        this.profileRepository = profileRepository;
        this.scoreEventRepository = scoreEventRepository;
        this.systemSnapshotRepository = systemSnapshotRepository;
        this.installedApplicationRepository = installedApplicationRepository;
        this.decisionResponseRepository = decisionResponseRepository;
        this.payloadRepository = payloadRepository;
        this.runRepository = runRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.blockingDb = blockingDb;
        this.requestContext = requestContext;
    }

    // ============================================================================
    // Trust Profile Endpoints
    // ============================================================================

    /**
     * Retrieves device trust profiles with optional filtering.
     * 
     * @param tenantId Optional tenant ID override (requires TENANT_ADMIN or PRODUCT_ADMIN role)
     * @param authentication Current user authentication
     * @param deviceExternalId Filter by device external ID
     * @param osType Filter by OS type (WINDOWS, MACOS, LINUX, ANDROID, IOS)
     * @param osName Filter by OS name (e.g., "Windows 11", "macOS Sonoma")
     * @param scoreBand Filter by trust score band (TRUSTED, HIGH_RISK, CRITICAL, UNKNOWN)
     * @param page Page number (0-indexed, max: 1000)
     * @param size Page size (1-500, default: 50)
     * @return Flux of matching device trust profiles
     * @throws ResponseStatusException 400 if invalid filter values provided
     * @throws ResponseStatusException 403 if TENANT_USER tries to access another user's device
     */
    @GetMapping("/devices/trust-profiles")
    public Flux<DeviceTrustProfile> listTrustProfiles(
            @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId,
            Authentication authentication,
            @RequestParam(name = "device_external_id", required = false) String deviceExternalId,
            @RequestParam(name = "os_type", required = false) String osType,
            @RequestParam(name = "os_name", required = false) String osName,
            @RequestParam(name = "score_band", required = false) String scoreBand,
            @RequestParam(name = "page", defaultValue = "${api.pagination.default-page:0}") int page,
            @RequestParam(name = "size", defaultValue = "${api.pagination.default-size:50}") int size
    ) {
        PageParams pageParams = PageParams.of(page, size, defaultSize, maxSize, maxPage);
        return resolveTenantContext(authentication, tenantId)
                .flatMapMany(ctx -> blockingDb.flux(() -> profileRepository.findPaged(
                        ctx.tenantId(),
                        normalizeOptionalText(deviceExternalId),
                        normalizeOsType(osType),
                        normalizeOptionalText(osName),
                        normalizeScoreBand(scoreBand),
                        ctx.ownerUserId(),
                        pageParams.limit(),
                        pageParams.offset()
                )));
    }

    /**
     * Retrieves a specific device trust profile by ID.
     * 
     * @param tenantId Optional tenant ID override (requires TENANT_ADMIN or PRODUCT_ADMIN role)
     * @param authentication Current user authentication
     * @param profileId The trust profile ID
     * @return Mono containing the trust profile
     * @throws ResponseStatusException 404 if profile not found
     * @throws ResponseStatusException 403 if TENANT_USER tries to access another user's device
     */
    @GetMapping("/devices/trust-profiles/{profile_id}")
    public Mono<DeviceTrustProfile> getTrustProfile(
            @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId,
            Authentication authentication,
            @PathVariable("profile_id") Long profileId
    ) {
        return resolveTenantContext(authentication, tenantId)
                .flatMap(ctx -> blockingDb.mono(() ->
                        profileRepository.findByIdAndTenant(profileId, ctx.tenantId()))
                        .flatMap(Mono::justOrEmpty)
                        .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND,
                                "Trust profile with id '%d' not found".formatted(profileId))))
                        .flatMap(profile -> enforceTenantUserDeviceScope(ctx, profile.getDeviceExternalId())
                                .thenReturn(profile)));
    }

    // ============================================================================
    // Trust Score Events Endpoints
    // ============================================================================

    /**
     * Retrieves trust score events for a specific device.
     * 
     * @param tenantId Optional tenant ID override (requires TENANT_ADMIN or PRODUCT_ADMIN role)
     * @param authentication Current user authentication
     * @param deviceExternalId The device external ID
     * @param page Page number (0-indexed, max: 1000)
     * @param size Page size (1-500, default: 50)
     * @return Flux of trust score events for the device
     * @throws ResponseStatusException 400 if deviceExternalId is missing
     * @throws ResponseStatusException 403 if TENANT_USER tries to access another user's device
     */
    @GetMapping("/devices/{device_external_id}/trust-score-events")
    public Flux<DeviceTrustScoreEvent> getTrustScoreEvents(
            @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId,
            Authentication authentication,
            @PathVariable("device_external_id") String deviceExternalId,
            @RequestParam(name = "page", defaultValue = "${api.pagination.default-page:0}") int page,
            @RequestParam(name = "size", defaultValue = "${api.pagination.default-size:50}") int size
    ) {
        PageParams pageParams = PageParams.of(page, size, defaultSize, maxSize, maxPage);
        String normalizedDeviceId = normalizeRequiredText(deviceExternalId);
        return resolveTenantContext(authentication, tenantId)
                .flatMapMany(ctx -> enforceTenantUserDeviceScope(ctx, normalizedDeviceId)
                        .thenMany(blockingDb.flux(() -> scoreEventRepository.findByDevice(
                                ctx.tenantId(), normalizedDeviceId, pageParams.limit(), pageParams.offset()))));
    }

    // ============================================================================
    // Device Decision Responses Endpoints
    // ============================================================================

    /**
     * Retrieves decision responses for a specific device.
     * 
     * @param tenantId Optional tenant ID override (requires TENANT_ADMIN or PRODUCT_ADMIN role)
     * @param authentication Current user authentication
     * @param deviceExternalId The device external ID
     * @param page Page number (0-indexed, max: 1000)
     * @param size Page size (1-500, default: 50)
     * @return Flux of decision responses for the device
     * @throws ResponseStatusException 400 if deviceExternalId is missing
     * @throws ResponseStatusException 403 if TENANT_USER tries to access another user's device
     */
    @GetMapping("/devices/{device_external_id}/decisions")
    public Flux<DeviceDecisionResponse> getDeviceDecisions(
            @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId,
            Authentication authentication,
            @PathVariable("device_external_id") String deviceExternalId,
            @RequestParam(name = "page", defaultValue = "${api.pagination.default-page:0}") int page,
            @RequestParam(name = "size", defaultValue = "${api.pagination.default-size:50}") int size
    ) {
        PageParams pageParams = PageParams.of(page, size, defaultSize, maxSize, maxPage);
        String normalizedDeviceId = normalizeRequiredText(deviceExternalId);
        return resolveTenantContext(authentication, tenantId)
                .flatMapMany(ctx -> enforceTenantUserDeviceScope(ctx, normalizedDeviceId)
                        .thenMany(blockingDb.flux(() -> decisionResponseRepository.findByDevice(
                                ctx.tenantId(), normalizedDeviceId, pageParams.limit(), pageParams.offset()))));
    }

    // ============================================================================
    // Device System Snapshot Endpoints
    // ============================================================================

    /**
     * Retrieves the latest system snapshot for a specific device.
     * 
     * @param tenantId Optional tenant ID override (requires TENANT_ADMIN or PRODUCT_ADMIN role)
     * @param authentication Current user authentication
     * @param deviceExternalId The device external ID
     * @return Mono containing the latest system snapshot
     * @throws ResponseStatusException 400 if deviceExternalId is missing
     * @throws ResponseStatusException 404 if no snapshot exists for the device
     * @throws ResponseStatusException 403 if TENANT_USER tries to access another user's device
     */
    @GetMapping("/devices/{device_external_id}/snapshots/latest")
    public Mono<DeviceSystemSnapshot> getLatestSnapshot(
            @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId,
            Authentication authentication,
            @PathVariable("device_external_id") String deviceExternalId
    ) {
        String normalizedDeviceId = normalizeRequiredText(deviceExternalId);
        return resolveTenantContext(authentication, tenantId)
                .flatMap(ctx -> enforceTenantUserDeviceScope(ctx, normalizedDeviceId)
                        .then(blockingDb.mono(() -> systemSnapshotRepository.findLatestByDevice(
                                ctx.tenantId(), normalizedDeviceId))
                        .flatMap(Mono::justOrEmpty))
                        .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND,
                                "No snapshot found for device '%s'".formatted(normalizedDeviceId)))));
    }

    /**
     * Retrieves paginated system snapshots for a specific device.
     * 
     * @param tenantId Optional tenant ID override (requires TENANT_ADMIN or PRODUCT_ADMIN role)
     * @param authentication Current user authentication
     * @param deviceExternalId The device external ID
     * @param page Page number (0-indexed, max: 1000)
     * @param size Page size (1-500, default: 50)
     * @return Flux of system snapshots for the device
     * @throws ResponseStatusException 400 if deviceExternalId is missing
     * @throws ResponseStatusException 403 if TENANT_USER tries to access another user's device
     */
    @GetMapping("/devices/{device_external_id}/snapshots")
    public Flux<DeviceSystemSnapshot> getDeviceSnapshots(
            @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId,
            Authentication authentication,
            @PathVariable("device_external_id") String deviceExternalId,
            @RequestParam(name = "page", defaultValue = "${api.pagination.default-page:0}") int page,
            @RequestParam(name = "size", defaultValue = "${api.pagination.default-size:50}") int size
    ) {
        PageParams pageParams = PageParams.of(page, size, defaultSize, maxSize, maxPage);
        String normalizedDeviceId = normalizeRequiredText(deviceExternalId);
        return resolveTenantContext(authentication, tenantId)
                .flatMapMany(ctx -> enforceTenantUserDeviceScope(ctx, normalizedDeviceId)
                        .thenMany(blockingDb.flux(() -> systemSnapshotRepository.findByDevice(
                                ctx.tenantId(), normalizedDeviceId, pageParams.limit(), pageParams.offset()))));
    }

    // ============================================================================
    // Device Installed Application Endpoints
    // ============================================================================

    /**
     * Retrieves the latest installed applications for a specific device.
     * 
     * @param tenantId Optional tenant ID override (requires TENANT_ADMIN or PRODUCT_ADMIN role)
     * @param authentication Current user authentication
     * @param deviceExternalId The device external ID
     * @param page Page number (0-indexed, max: 1000)
     * @param size Page size (1-500, default: 50)
     * @return Flux of installed applications for the device
     * @throws ResponseStatusException 400 if deviceExternalId is missing
     * @throws ResponseStatusException 403 if TENANT_USER tries to access another user's device
     */
    @GetMapping("/devices/{device_external_id}/installed-apps/latest")
    public Flux<DeviceInstalledApplication> getLatestInstalledApps(
            @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId,
            Authentication authentication,
            @PathVariable("device_external_id") String deviceExternalId,
            @RequestParam(name = "page", defaultValue = "${api.pagination.default-page:0}") int page,
            @RequestParam(name = "size", defaultValue = "${api.pagination.default-size:50}") int size
    ) {
        PageParams pageParams = PageParams.of(page, size, defaultSize, maxSize, maxPage);
        String normalizedDeviceId = normalizeRequiredText(deviceExternalId);
        return resolveTenantContext(authentication, tenantId)
                .flatMapMany(ctx -> enforceTenantUserDeviceScope(ctx, normalizedDeviceId)
                        .thenMany(blockingDb.flux(() -> installedApplicationRepository.findLatestAppsByDevice(
                                ctx.tenantId(), normalizedDeviceId, pageParams.limit(), pageParams.offset()))));
    }

    /**
     * Retrieves installed applications for a specific device with optional status filtering.
     * 
     * @param tenantId Optional tenant ID override (requires TENANT_ADMIN or PRODUCT_ADMIN role)
     * @param authentication Current user authentication
     * @param deviceExternalId The device external ID
     * @param status Filter by application status (ACTIVE, INACTIVE, PENDING, REMOVED)
     * @param page Page number (0-indexed, max: 1000)
     * @param size Page size (1-500, default: 50)
     * @return Flux of installed applications for the device
     * @throws ResponseStatusException 400 if deviceExternalId is missing or invalid status
     * @throws ResponseStatusException 403 if TENANT_USER tries to access another user's device
     */
    @GetMapping("/devices/{device_external_id}/installed-apps")
    public Flux<DeviceInstalledApplication> getDeviceInstalledApps(
            @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId,
            Authentication authentication,
            @PathVariable("device_external_id") String deviceExternalId,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "page", defaultValue = "${api.pagination.default-page:0}") int page,
            @RequestParam(name = "size", defaultValue = "${api.pagination.default-size:50}") int size
    ) {
        PageParams pageParams = PageParams.of(page, size, defaultSize, maxSize, maxPage);
        String normalizedDeviceId = normalizeRequiredText(deviceExternalId);
        String normalizedStatus = normalizeAppStatus(status);
        return resolveTenantContext(authentication, tenantId)
                .flatMapMany(ctx -> enforceTenantUserDeviceScope(ctx, normalizedDeviceId)
                        .thenMany(blockingDb.flux(() -> {
                            if (normalizedStatus == null) {
                                // Avoid nullable-status SQL binding edge-cases when no filter is requested.
                                return installedApplicationRepository.findLatestAppsByDevice(
                                        ctx.tenantId(), normalizedDeviceId, pageParams.limit(), pageParams.offset()
                                );
                            }
                            return installedApplicationRepository.findAppsByDevice(
                                    ctx.tenantId(), normalizedDeviceId, normalizedStatus, pageParams.limit(), pageParams.offset()
                            );
                        })));
    }

    // ============================================================================
    // Device Posture Payload Endpoints
    // ============================================================================

    /**
     * Retrieves posture payloads for a specific device.
     * 
     * @param tenantId Optional tenant ID override (requires TENANT_ADMIN or PRODUCT_ADMIN role)
     * @param authentication Current user authentication
     * @param deviceExternalId The device external ID
     * @param page Page number (0-indexed, max: 1000)
     * @param size Page size (1-500, default: 50)
     * @return Flux of posture payloads for the device
     * @throws ResponseStatusException 400 if deviceExternalId is missing
     * @throws ResponseStatusException 403 if TENANT_USER tries to access another user's device
     */
    @GetMapping("/devices/{device_external_id}/posture-payloads")
    public Flux<DevicePosturePayload> getDevicePosturePayloads(
            @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId,
            Authentication authentication,
            @PathVariable("device_external_id") String deviceExternalId,
            @RequestParam(name = "page", defaultValue = "${api.pagination.default-page:0}") int page,
            @RequestParam(name = "size", defaultValue = "${api.pagination.default-size:50}") int size
    ) {
        PageParams pageParams = PageParams.of(page, size, defaultSize, maxSize, maxPage);
        String normalizedDeviceId = normalizeRequiredText(deviceExternalId);
        return resolveTenantContext(authentication, tenantId)
                .flatMapMany(ctx -> enforceTenantUserDeviceScope(ctx, normalizedDeviceId)
                        .thenMany(blockingDb.flux(() -> payloadRepository.findByDevice(
                                ctx.tenantId(), normalizedDeviceId, pageParams.limit(), pageParams.offset()))));
    }

    // ============================================================================
    // Device Evaluation Run Endpoints
    // ============================================================================

    /**
     * Retrieves posture evaluation runs for a specific device.
     * 
     * @param tenantId Optional tenant ID override (requires TENANT_ADMIN or PRODUCT_ADMIN role)
     * @param authentication Current user authentication
     * @param deviceExternalId The device external ID
     * @param page Page number (0-indexed, max: 1000)
     * @param size Page size (1-500, default: 50)
     * @return Flux of evaluation runs for the device
     * @throws ResponseStatusException 400 if deviceExternalId is missing
     * @throws ResponseStatusException 403 if TENANT_USER tries to access another user's device
     */
    @GetMapping("/devices/{device_external_id}/evaluation-runs")
    public Flux<PostureEvaluationRun> getDeviceEvaluationRuns(
            @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId,
            Authentication authentication,
            @PathVariable("device_external_id") String deviceExternalId,
            @RequestParam(name = "page", defaultValue = "${api.pagination.default-page:0}") int page,
            @RequestParam(name = "size", defaultValue = "${api.pagination.default-size:50}") int size
    ) {
        PageParams pageParams = PageParams.of(page, size, defaultSize, maxSize, maxPage);
        String normalizedDeviceId = normalizeRequiredText(deviceExternalId);
        return resolveTenantContext(authentication, tenantId)
                .flatMapMany(ctx -> enforceTenantUserDeviceScope(ctx, normalizedDeviceId)
                        .thenMany(blockingDb.flux(() -> runRepository.findByDevice(
                                ctx.tenantId(), normalizedDeviceId, pageParams.limit(), pageParams.offset()))));
    }

    // ============================================================================
    // Helper Methods - Context Resolution
    // ============================================================================

    /**
     * Resolves tenant context and user ownership for the current request.
     */
    private Mono<TenantContext> resolveTenantContext(Authentication authentication, String tenantId) {
        UserPrincipal principal = requestContext.requireUserPrincipal(authentication);
        Long ownerUserId = isTenantUser(principal) ? requirePositive(principal.id()) : null;

        return requestContext.resolveTenantId(authentication, tenantId)
                .map(resolvedTenantId -> new TenantContext(resolvedTenantId, ownerUserId));
    }

    /**
     * Enforces that TENANT_USER can only access devices they own.
     * Returns empty Mono if access is allowed, error if denied.
     * Uses FORBIDDEN (not NOT_FOUND) to prevent information leakage about device existence.
     */
    private Mono<Void> enforceTenantUserDeviceScope(TenantContext ctx, String deviceExternalId) {
        if (ctx.ownerUserId() == null) {
            return Mono.empty();
        }
        return blockingDb.mono(() -> enrollmentRepository.countActiveByTenantAndEnrollmentNoAndOwnerUserId(
                        ctx.tenantId(), deviceExternalId, ctx.ownerUserId()))
                .flatMap(count -> count > 0L
                        ? Mono.<Void>empty()
                        : Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN,
                                "Access denied: You do not have permission to access this device")));
    }

    // ============================================================================
    // Helper Methods - Parameter Normalization
    // ============================================================================

    private String normalizeRequiredText(String value) {
        String normalized = normalizeOptionalText(value);
        if (normalized == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Required parameter is missing");
        }
        return normalized;
    }

    private String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    /**
     * Validates and normalizes OS type parameter.
     */
    private String normalizeOsType(String value) {
        String normalized = normalizeOptionalText(value);
        if (normalized != null && !VALID_OS_TYPES.contains(normalized.toUpperCase())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid os_type '%s'. Valid values are: %s".formatted(normalized, String.join(", ", VALID_OS_TYPES)));
        }
        return normalized != null ? normalized.toUpperCase() : null;
    }

    /**
     * Validates and normalizes score band parameter.
     */
    private String normalizeScoreBand(String value) {
        String normalized = normalizeOptionalText(value);
        if (normalized != null && !VALID_SCORE_BANDS.contains(normalized.toUpperCase())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid score_band '%s'. Valid values are: %s".formatted(normalized, String.join(", ", VALID_SCORE_BANDS)));
        }
        return normalized != null ? normalized.toUpperCase() : null;
    }

    /**
     * Validates and normalizes app status parameter.
     */
    private String normalizeAppStatus(String value) {
        String normalized = normalizeOptionalText(value);
        if (normalized != null && !VALID_APP_STATUSES.contains(normalized.toUpperCase())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid status '%s'. Valid values are: %s".formatted(normalized, String.join(", ", VALID_APP_STATUSES)));
        }
        return normalized != null ? normalized.toUpperCase() : null;
    }

    private Long requirePositive(Long value) {
        if (value == null || value <= 0L) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User ID must be positive");
        }
        return value;
    }

    private boolean isTenantUser(UserPrincipal principal) {
        return principal != null && Role.TENANT_USER.name().equalsIgnoreCase(principal.role());
    }
}
