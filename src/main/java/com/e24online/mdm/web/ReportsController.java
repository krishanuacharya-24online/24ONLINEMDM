package com.e24online.mdm.web;

import com.e24online.mdm.records.EmbedConfig;
import com.e24online.mdm.records.user.guest.GuestTokenResponse;
import com.e24online.mdm.records.user.UserPrincipal;
import com.e24online.mdm.service.SupersetReportingService;
import com.e24online.mdm.web.security.AuthenticatedRequestContext;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("${api.version.prefix:v1}/reports")
@PreAuthorize("hasAnyRole('PRODUCT_ADMIN','TENANT_ADMIN')")
public class ReportsController {

    private final SupersetReportingService supersetReportingService;
    private final AuthenticatedRequestContext requestContext;

    public ReportsController(SupersetReportingService supersetReportingService,
                             AuthenticatedRequestContext requestContext) {
        this.supersetReportingService = supersetReportingService;
        this.requestContext = requestContext;
    }

    @GetMapping("/superset/config")
    public Mono<EmbedConfig> getSupersetEmbedConfig() {
        return Mono.just(supersetReportingService.embedConfig());
    }

    @PostMapping("/superset/guest-token")
    public Mono<GuestTokenResponse> createSupersetGuestToken(
            Authentication authentication,
            @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId
    ) {
        UserPrincipal principal = requestContext.requireUserPrincipal(authentication);
        return requestContext.resolveOptionalTenantId(authentication, tenantId)
                .defaultIfEmpty("")
                .map(this::normalizeOptionalTenantId)
                .flatMap(resolvedTenantId -> supersetReportingService.createGuestToken(principal, resolvedTenantId))
                .map(token -> new GuestTokenResponse(token.token(), token.resourceId()))
                .onErrorMap(IllegalStateException.class, ex ->
                        new ResponseStatusException(HttpStatus.BAD_GATEWAY, ex.getMessage()));
    }

    private String normalizeOptionalTenantId(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            return null;
        }
        return tenantId;
    }

}
