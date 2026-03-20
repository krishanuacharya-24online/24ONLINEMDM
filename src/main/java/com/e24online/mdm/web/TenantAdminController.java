package com.e24online.mdm.web;

import com.e24online.mdm.records.tenant.TenantKeyMetadataResponse;
import com.e24online.mdm.records.tenant.TenantKeyRotateResponse;
import com.e24online.mdm.records.tenant.TenantResponse;
import com.e24online.mdm.records.tenant.TenantFeatureOverrideRequest;
import com.e24online.mdm.records.tenant.TenantFeatureOverrideResponse;
import com.e24online.mdm.records.tenant.TenantSubscriptionResponse;
import com.e24online.mdm.records.tenant.TenantSubscriptionUpsertRequest;
import com.e24online.mdm.records.tenant.TenantUpsertRequest;
import com.e24online.mdm.records.tenant.TenantUsageResponse;
import com.e24online.mdm.records.tenant.SubscriptionPlanResponse;
import com.e24online.mdm.service.TenantAdminService;
import com.e24online.mdm.service.TenantEntitlementService;
import com.e24online.mdm.service.TenantSubscriptionService;
import com.e24online.mdm.web.security.AuthenticatedRequestContext;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("${api.version.prefix:v1}/admin/tenants")
@PreAuthorize("hasRole('PRODUCT_ADMIN')")
public class TenantAdminController {

    private final TenantAdminService tenantAdminService;
    private final TenantSubscriptionService tenantSubscriptionService;
    private final TenantEntitlementService tenantEntitlementService;
    private final AuthenticatedRequestContext requestContext;

    public TenantAdminController(TenantAdminService tenantAdminService,
                                 TenantSubscriptionService tenantSubscriptionService,
                                 TenantEntitlementService tenantEntitlementService,
                                 AuthenticatedRequestContext requestContext) {
        this.tenantAdminService = tenantAdminService;
        this.tenantSubscriptionService = tenantSubscriptionService;
        this.tenantEntitlementService = tenantEntitlementService;
        this.requestContext = requestContext;
    }

    @GetMapping
    public Flux<TenantResponse> listTenants(
            Authentication authentication,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "50") int size
    ) {
        String actor = requestContext.resolveActor(authentication);
        return tenantAdminService.listTenants(page, size, actor);
    }

    @GetMapping("/subscription-plans")
    public Flux<SubscriptionPlanResponse> listSubscriptionPlans() {
        return tenantSubscriptionService.listPlans();
    }

    @PostMapping
    public Mono<TenantResponse> createTenant(
            Authentication authentication,
            @Valid @RequestBody Mono<TenantUpsertRequest> request
    ) {
        String actor = requestContext.resolveActor(authentication);
        return request.flatMap(body -> tenantAdminService.createTenant(
                actor,
                body.tenantId(),
                body.name(),
                body.status()
        ));
    }

    @PutMapping("/{id}")
    public Mono<TenantResponse> updateTenant(
            Authentication authentication,
            @PathVariable("id") Long id,
            @Valid @RequestBody Mono<TenantUpsertRequest> request
    ) {
        String actor = requestContext.resolveActor(authentication);
        return request.flatMap(body -> tenantAdminService.updateTenant(
                id,
                actor,
                body.tenantId(),
                body.name(),
                body.status()
        ));
    }

    @DeleteMapping("/{id}")
    public Mono<Void> deleteTenant(
            Authentication authentication,
            @PathVariable("id") Long id
    ) {
        String actor = requestContext.resolveActor(authentication);
        return tenantAdminService.deleteTenant(id, actor);
    }

    @GetMapping("/{id}/keys/active")
    public Mono<TenantKeyMetadataResponse> getActiveTenantKey(
            Authentication authentication,
            @PathVariable("id") Long id
    ) {
        String actor = requestContext.resolveActor(authentication);
        return tenantAdminService.getActiveTenantKey(id, actor);
    }

    @PostMapping("/{id}/keys/rotate")
    public Mono<TenantKeyRotateResponse> rotateTenantKey(
            Authentication authentication,
            @PathVariable("id") Long id
    ) {
        String actor = requestContext.resolveActor(authentication);
        return tenantAdminService.rotateTenantKey(id, actor);
    }

    @GetMapping("/{id}/subscription")
    public Mono<TenantSubscriptionResponse> getSubscription(@PathVariable("id") Long id) {
        return tenantSubscriptionService.getSubscription(id);
    }

    @PutMapping("/{id}/subscription")
    public Mono<TenantSubscriptionResponse> upsertSubscription(
            Authentication authentication,
            @PathVariable("id") Long id,
            @RequestBody Mono<TenantSubscriptionUpsertRequest> request
    ) {
        String actor = requestContext.resolveActor(authentication);
        return request.flatMap(body -> tenantSubscriptionService.upsertSubscription(id, actor, body));
    }

    @GetMapping("/{id}/usage")
    public Mono<TenantUsageResponse> getUsage(@PathVariable("id") Long id) {
        return tenantEntitlementService.getTenantUsage(id);
    }

    @GetMapping("/{id}/feature-overrides")
    public Mono<java.util.List<TenantFeatureOverrideResponse>> listFeatureOverrides(@PathVariable("id") Long id) {
        return tenantSubscriptionService.listFeatureOverrides(id);
    }

    @PutMapping("/{id}/feature-overrides/{featureKey}")
    public Mono<TenantFeatureOverrideResponse> upsertFeatureOverride(
            Authentication authentication,
            @PathVariable("id") Long id,
            @PathVariable("featureKey") String featureKey,
            @RequestBody Mono<TenantFeatureOverrideRequest> request
    ) {
        String actor = requestContext.resolveActor(authentication);
        return request.flatMap(body -> tenantSubscriptionService.upsertFeatureOverride(id, featureKey, actor, body));
    }


}
