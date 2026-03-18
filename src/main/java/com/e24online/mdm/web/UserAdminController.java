package com.e24online.mdm.web;

import com.e24online.mdm.records.user.UserResponse;
import com.e24online.mdm.service.UserAdminService;
import com.e24online.mdm.web.security.AuthenticatedRequestContext;
import com.e24online.mdm.records.user.UserPrincipal;
import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("${api.version.prefix:v1}/admin/users")
@PreAuthorize("hasAnyRole('PRODUCT_ADMIN','TENANT_ADMIN')")
public class UserAdminController {

    private final UserAdminService userAdminService;
    private final AuthenticatedRequestContext requestContext;

    public UserAdminController(UserAdminService userAdminService,
                               AuthenticatedRequestContext requestContext) {
        this.userAdminService = userAdminService;
        this.requestContext = requestContext;
    }

    @GetMapping
    public Flux<UserResponse> listUsers(
            @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId,
            Authentication authentication,
            @RequestParam(name = "role", required = false) String role,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "tenant_id", required = false) String tenantIdFilter,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "50") int size
    ) {
        UserPrincipal principal = requestContext.requireUserPrincipal(authentication);
        String effectiveTenantFilter = tenantIdFilter;
        if ((effectiveTenantFilter == null || effectiveTenantFilter.isBlank()) && tenantId != null && !tenantId.isBlank()) {
            effectiveTenantFilter = tenantId;
        }
        return userAdminService.listUsers(principal, role, status, effectiveTenantFilter, page, size);
    }

    @GetMapping("/{id}")
    public Mono<UserResponse> getUser(
            Authentication authentication,
            @PathVariable("id") Long id
    ) {
        UserPrincipal principal = requestContext.requireUserPrincipal(authentication);
        return userAdminService.getUser(id, principal);
    }

    @PostMapping
    public Mono<UserResponse> createUser(
            Authentication authentication,
            @Valid @RequestBody Mono<UserCreateRequest> request
    ) {
        UserPrincipal principal = requestContext.requireUserPrincipal(authentication);
        return request.flatMap(body -> userAdminService.createUser(
                principal,
                body.username(),
                body.password(),
                body.role(),
                body.status(),
                body.tenantId()
        ));
    }

    @PutMapping("/{id}")
    public Mono<UserResponse> updateUser(
            Authentication authentication,
            @PathVariable("id") Long id,
            @Valid @RequestBody Mono<UserUpdateRequest> request
    ) {
        UserPrincipal principal = requestContext.requireUserPrincipal(authentication);
        return request.flatMap(body -> userAdminService.updateUser(
                id,
                principal,
                body.role(),
                body.status(),
                body.tenantId(),
                body.password()
        ));
    }

    @DeleteMapping("/{id}")
    public Mono<Void> deleteUser(
            Authentication authentication,
            @PathVariable("id") Long id
    ) {
        UserPrincipal principal = requestContext.requireUserPrincipal(authentication);
        return userAdminService.deleteUser(id, principal);
    }

    public record UserCreateRequest(
            @NotBlank String username,
            @NotBlank String password,
            @NotBlank String role,
            String status,
            @JsonAlias({"tenant_id", "tenantId"})
            String tenantId
    ) {
    }

    public record UserUpdateRequest(
            @NotBlank String role,
            String status,
            @JsonAlias({"tenant_id", "tenantId"})
            String tenantId,
            String password
    ) {
    }
}
