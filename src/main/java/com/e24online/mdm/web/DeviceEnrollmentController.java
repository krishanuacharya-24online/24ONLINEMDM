package com.e24online.mdm.web;

import com.e24online.mdm.domain.DeviceEnrollment;
import com.e24online.mdm.records.CreateSetupKeyRequest;
import com.e24online.mdm.records.DeEnrollRequest;
import com.e24online.mdm.records.devices.DeviceTokenRotation;
import com.e24online.mdm.records.SetupKeyIssue;
import com.e24online.mdm.service.DeviceEnrollmentService;
import com.e24online.mdm.web.security.AuthenticatedRequestContext;
import com.e24online.mdm.records.user.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("${api.version.prefix:v1}/devices/enrollments")
@PreAuthorize("hasAnyRole('TENANT_ADMIN','TENANT_USER')")
public class DeviceEnrollmentController {

    private final DeviceEnrollmentService enrollmentService;
    private final AuthenticatedRequestContext requestContext;

    public DeviceEnrollmentController(DeviceEnrollmentService enrollmentService,
                                      AuthenticatedRequestContext requestContext) {
        this.enrollmentService = enrollmentService;
        this.requestContext = requestContext;
    }

    @PostMapping("/setup-keys")
    public Mono<SetupKeyIssue> createSetupKey(
            @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId,
            Authentication authentication,
            @Valid @RequestBody Mono<CreateSetupKeyRequest> request
    ) {
        UserPrincipal principal = requestContext.requireUserPrincipal(authentication);
        String actor = requestContext.resolveActor(authentication);
        return requestContext.resolveTenantId(authentication, tenantId)
                .flatMap(resolvedTenantId -> request.flatMap(body ->
                        enrollmentService.createSetupKeyAsync(
                                resolvedTenantId,
                                principal.id(),
                                resolveIssueTargetUserId(principal, body.targetUserId()),
                                actor,
                                body.maxUses(),
                                body.ttlMinutes()
                        )));
    }

    @GetMapping
    public Flux<DeviceEnrollment> listEnrollments(
            @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId,
            Authentication authentication,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "user_id", required = false) Long userId,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "50") int size
    ) {
        UserPrincipal principal = requestContext.requireUserPrincipal(authentication);
        Long ownerFilter = resolveOwnerFilter(principal, userId);
        return requestContext.resolveTenantId(authentication, tenantId)
                .flatMapMany(resolvedTenantId -> enrollmentService.listEnrollmentsAsync(
                        resolvedTenantId,
                        status,
                        ownerFilter,
                        page,
                        size
                ));
    }

    @GetMapping("/{id}")
    public Mono<DeviceEnrollment> getEnrollment(
            @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId,
            Authentication authentication,
            @PathVariable("id") Long id
    ) {
        UserPrincipal principal = requestContext.requireUserPrincipal(authentication);
        Long ownerFilter = isTenantUser(principal) ? requirePositive(principal.id(), "user_id") : null;
        return requestContext.resolveTenantId(authentication, tenantId)
                .flatMap(resolvedTenantId -> enrollmentService.getEnrollmentAsync(resolvedTenantId, id, ownerFilter));
    }

    @PostMapping("/{id}/de-enroll")
    public Mono<DeviceEnrollment> deEnroll(
            @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId,
            Authentication authentication,
            @PathVariable("id") Long id,
            @Valid @RequestBody Mono<DeEnrollRequest> request
    ) {
        UserPrincipal principal = requestContext.requireUserPrincipal(authentication);
        Long ownerFilter = isTenantUser(principal) ? requirePositive(principal.id(), "user_id") : null;
        String actor = requestContext.resolveActor(authentication);
        return requestContext.resolveTenantId(authentication, tenantId)
                .flatMap(resolvedTenantId -> request.flatMap(body ->
                        enrollmentService.deEnrollAsync(resolvedTenantId, actor, ownerFilter, id, body.reason())));
    }

    @PostMapping("/{id}/device-token/rotate")
    public Mono<DeviceTokenRotation> rotateDeviceToken(
            @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId,
            Authentication authentication,
            @PathVariable("id") Long id
    ) {
        UserPrincipal principal = requestContext.requireUserPrincipal(authentication);
        Long ownerFilter = isTenantUser(principal) ? requirePositive(principal.id(), "user_id") : null;
        String actor = requestContext.resolveActor(authentication);
        return requestContext.resolveTenantId(authentication, tenantId)
                .flatMap(resolvedTenantId -> enrollmentService.rotateDeviceTokenAsync(
                        resolvedTenantId,
                        actor,
                        ownerFilter,
                        id
                ));
    }

    private Long resolveIssueTargetUserId(UserPrincipal principal, Long requestedTargetUserId) {
        if (isTenantUser(principal)) {
            Long principalUserId = requirePositive(principal.id(), "user_id");
            if (requestedTargetUserId == null) {
                return principalUserId;
            }
            Long normalizedRequested = requirePositive(requestedTargetUserId, "target_user_id");
            if (!principalUserId.equals(normalizedRequested)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "TENANT_USER can only issue keys for self");
            }
            return normalizedRequested;
        }
        if (isTenantAdmin(principal)) {
            return requirePositive(requestedTargetUserId, "target_user_id");
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Unsupported role");
    }

    private Long resolveOwnerFilter(UserPrincipal principal, Long requestedUserId) {
        if (isTenantUser(principal)) {
            Long principalUserId = requirePositive(principal.id(), "user_id");
            if (requestedUserId != null && !principalUserId.equals(requestedUserId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "TENANT_USER can only view own enrollments");
            }
            return principalUserId;
        }
        if (isTenantAdmin(principal)) {
            return requestedUserId == null ? null : requirePositive(requestedUserId, "user_id");
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Unsupported role");
    }

    private boolean isTenantUser(UserPrincipal principal) {
        return principal != null && "TENANT_USER".equalsIgnoreCase(principal.role());
    }

    private boolean isTenantAdmin(UserPrincipal principal) {
        return principal != null && "TENANT_ADMIN".equalsIgnoreCase(principal.role());
    }

    private Long requirePositive(Long value, String fieldName) {
        if (value == null || value <= 0L) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " must be positive");
        }
        return value;
    }
}
