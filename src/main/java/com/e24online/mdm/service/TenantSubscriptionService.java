package com.e24online.mdm.service;

import com.e24online.mdm.domain.SubscriptionPlan;
import com.e24online.mdm.domain.Tenant;
import com.e24online.mdm.domain.TenantFeatureOverride;
import com.e24online.mdm.domain.TenantSubscription;
import com.e24online.mdm.records.tenant.*;
import com.e24online.mdm.repository.SubscriptionPlanRepository;
import com.e24online.mdm.repository.TenantFeatureOverrideRepository;
import com.e24online.mdm.repository.TenantRepository;
import com.e24online.mdm.repository.TenantSubscriptionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class TenantSubscriptionService {

    public static final String DEFAULT_PLAN_CODE = "TRIAL";
    public static final String FEATURE_PREMIUM_REPORTING = "PREMIUM_REPORTING";
    public static final String FEATURE_ADVANCED_CONTROLS = "ADVANCED_CONTROLS";

    private static final int DEFAULT_TRIAL_DAYS = 30;
    private static final int DEFAULT_GRACE_DAYS = 7;
    private static final Pattern PLAN_CODE_PATTERN = Pattern.compile("^[A-Z0-9][A-Z0-9_-]{1,63}$");

    private final TenantRepository tenantRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final TenantSubscriptionRepository tenantSubscriptionRepository;
    private final TenantFeatureOverrideRepository featureOverrideRepository;
    private final BlockingDb blockingDb;
    private final AuditEventService auditEventService;

    public TenantSubscriptionService(TenantRepository tenantRepository,
                                     SubscriptionPlanRepository subscriptionPlanRepository,
                                     TenantSubscriptionRepository tenantSubscriptionRepository,
                                     TenantFeatureOverrideRepository featureOverrideRepository,
                                     BlockingDb blockingDb,
                                     AuditEventService auditEventService) {
        this.tenantRepository = tenantRepository;
        this.subscriptionPlanRepository = subscriptionPlanRepository;
        this.tenantSubscriptionRepository = tenantSubscriptionRepository;
        this.featureOverrideRepository = featureOverrideRepository;
        this.blockingDb = blockingDb;
        this.auditEventService = auditEventService;
    }

    public Flux<SubscriptionPlanResponse> listPlans() {
        return blockingDb.flux(subscriptionPlanRepository::findAllActive)
                .map(this::toPlanResponse);
    }

    public Flux<SubscriptionPlanAdminResponse> listPlanCatalog() {
        return blockingDb.flux(subscriptionPlanRepository::findAllAvailable)
                .map(this::toAdminPlanResponse);
    }

    public Mono<SubscriptionPlanAdminResponse> createPlan(String actor, SubscriptionPlanUpsertRequest request) {
        return blockingDb.mono(() -> createPlanBlocking(actor, request));
    }

    public Mono<SubscriptionPlanAdminResponse> updatePlan(Long planId,
                                                          String actor,
                                                          SubscriptionPlanUpsertRequest request) {
        return blockingDb.mono(() -> updatePlanBlocking(planId, actor, request));
    }

    public Mono<SubscriptionPlanAdminResponse> retirePlan(Long planId, String actor) {
        return blockingDb.mono(() -> retirePlanBlocking(planId, actor));
    }

    public Mono<TenantSubscriptionResponse> getSubscription(Long tenantMasterId) {
        return blockingDb.mono(() -> toSubscriptionResponse(loadResolvedSubscription(tenantMasterId)));
    }

    public Mono<TenantSubscriptionResponse> upsertSubscription(Long tenantMasterId,
                                                               String actor,
                                                               TenantSubscriptionUpsertRequest request) {
        return blockingDb.mono(() -> upsertSubscriptionBlocking(tenantMasterId, actor, request));
    }

    public Mono<List<TenantFeatureOverrideResponse>> listFeatureOverrides(Long tenantMasterId) {
        return blockingDb.mono(() -> {
            Tenant tenant = requireActiveTenantById(tenantMasterId);
            return featureOverrideRepository.findByTenantMasterId(tenant.getId()).stream()
                    .map(this::toFeatureResponse)
                    .toList();
        });
    }

    public Mono<TenantFeatureOverrideResponse> upsertFeatureOverride(Long tenantMasterId,
                                                                     String featureKey,
                                                                     String actor,
                                                                     TenantFeatureOverrideRequest request) {
        return blockingDb.mono(() -> upsertFeatureOverrideBlocking(tenantMasterId, featureKey, actor, request));
    }

    public TenantSubscription ensureSubscriptionForTenant(Tenant tenant, String actor) {
        if (tenant == null || tenant.getId() == null) {
            throw new IllegalArgumentException("tenant is required");
        }
        return tenantSubscriptionRepository.findByTenantMasterId(tenant.getId())
                .orElseGet(() -> createDefaultSubscription(tenant, normalizeActor(actor)));
    }

    public ResolvedSubscription loadResolvedSubscription(Long tenantMasterId) {
        Tenant tenant = requireActiveTenantById(tenantMasterId);
        return loadResolvedSubscription(tenant);
    }

    public ResolvedSubscription loadResolvedSubscriptionByTenantCode(String tenantCode) {
        Tenant tenant = requireActiveTenantByCode(tenantCode);
        return loadResolvedSubscription(tenant);
    }

    private ResolvedSubscription loadResolvedSubscription(Tenant tenant) {
        TenantSubscription subscription = tenantSubscriptionRepository.findByTenantMasterId(tenant.getId())
                .orElseGet(() -> ensureSubscriptionForTenant(tenant, "system"));
        SubscriptionPlan plan = subscriptionPlanRepository.findAvailableById(subscription.getSubscriptionPlanId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Subscription plan is missing"));
        return new ResolvedSubscription(tenant, subscription, plan, loadActiveFeatureOverrides(tenant.getId()));
    }

    private SubscriptionPlanAdminResponse createPlanBlocking(String actor, SubscriptionPlanUpsertRequest request) {
        String effectiveActor = normalizeActor(actor);
        String planCode = normalizeManagedPlanCode(request.planCode());
        if (subscriptionPlanRepository.findAvailableByPlanCode(planCode).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "plan_code already exists");
        }

        OffsetDateTime now = OffsetDateTime.now();
        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setPlanCode(planCode);
        applyPlanFields(plan, request, true);
        plan.setCreatedAt(now);
        plan.setCreatedBy(effectiveActor);
        plan.setModifiedAt(now);
        plan.setModifiedBy(effectiveActor);
        SubscriptionPlan saved = subscriptionPlanRepository.save(plan);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("planCode", saved.getPlanCode());
        metadata.put("planName", saved.getPlanName());
        metadata.put("status", saved.getStatus());
        recordPlanAudit("SUBSCRIPTION_PLAN_CREATED", "CREATE", effectiveActor, saved, metadata);
        return toAdminPlanResponse(saved);
    }

    private SubscriptionPlanAdminResponse updatePlanBlocking(Long planId,
                                                             String actor,
                                                             SubscriptionPlanUpsertRequest request) {
        SubscriptionPlan existing = requireManagedPlan(planId);
        String effectiveActor = normalizeActor(actor);
        String nextPlanCode = normalizeManagedPlanCode(request.planCode());
        subscriptionPlanRepository.findAvailableByPlanCode(nextPlanCode)
                .filter(plan -> !plan.getId().equals(existing.getId()))
                .ifPresent(plan -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "plan_code already exists");
                });

        String beforePlanCode = existing.getPlanCode();
        String beforePlanName = existing.getPlanName();
        String beforeStatus = existing.getStatus();
        existing.setPlanCode(nextPlanCode);
        applyPlanFields(existing, request, false);
        existing.setModifiedAt(OffsetDateTime.now());
        existing.setModifiedBy(effectiveActor);
        guardAtLeastOneActivePlan(existing, beforeStatus);
        SubscriptionPlan saved = subscriptionPlanRepository.save(existing);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("beforePlanCode", beforePlanCode);
        metadata.put("afterPlanCode", saved.getPlanCode());
        metadata.put("beforePlanName", beforePlanName);
        metadata.put("afterPlanName", saved.getPlanName());
        metadata.put("beforeStatus", beforeStatus);
        metadata.put("afterStatus", saved.getStatus());
        recordPlanAudit("SUBSCRIPTION_PLAN_UPDATED", "UPDATE", effectiveActor, saved, metadata);
        return toAdminPlanResponse(saved);
    }

    private SubscriptionPlanAdminResponse retirePlanBlocking(Long planId, String actor) {
        SubscriptionPlan existing = requireManagedPlan(planId);
        if ("INACTIVE".equalsIgnoreCase(existing.getStatus())) {
            return toAdminPlanResponse(existing);
        }

        String effectiveActor = normalizeActor(actor);
        String beforeStatus = existing.getStatus();
        existing.setStatus("INACTIVE");
        guardAtLeastOneActivePlan(existing, beforeStatus);
        existing.setModifiedAt(OffsetDateTime.now());
        existing.setModifiedBy(effectiveActor);
        SubscriptionPlan saved = subscriptionPlanRepository.save(existing);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("planCode", saved.getPlanCode());
        metadata.put("planName", saved.getPlanName());
        metadata.put("status", saved.getStatus());
        recordPlanAudit("SUBSCRIPTION_PLAN_RETIRED", "RETIRE", effectiveActor, saved, metadata);
        return toAdminPlanResponse(saved);
    }

    private TenantSubscriptionResponse upsertSubscriptionBlocking(Long tenantMasterId,
                                                                  String actor,
                                                                  TenantSubscriptionUpsertRequest request) {
        ResolvedSubscription resolved = loadResolvedSubscription(tenantMasterId);
        TenantSubscription subscription = resolved.subscription();
        String effectiveActor = normalizeActor(actor);
        SubscriptionPlan beforePlan = resolved.plan();
        String beforeState = subscription.getSubscriptionState();
        SubscriptionPlan nextPlan = subscriptionPlanRepository.findActiveByPlanCode(normalizePlanCode(request.planCode()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "plan_code is invalid"));
        String nextState = normalizeSubscriptionState(request.subscriptionState());
        OffsetDateTime now = OffsetDateTime.now();

        subscription.setSubscriptionPlanId(nextPlan.getId());
        subscription.setSubscriptionState(nextState);
        subscription.setStartedAt(subscription.getStartedAt() == null ? now : subscription.getStartedAt());

        OffsetDateTime requestStart = request.currentPeriodStart();

        if (requestStart != null) {
            subscription.setCurrentPeriodStart(requestStart);
        } else if (subscription.getCurrentPeriodStart() == null) {
            subscription.setCurrentPeriodStart(now);
        }

        if (request.currentPeriodEnd() != null) {
            subscription.setCurrentPeriodEnd(request.currentPeriodEnd());
        }
        subscription.setGraceEndsAt(
                "GRACE".equals(nextState) ? request.graceEndsAt() : null
        );
        subscription.setSuspendedAt("SUSPENDED".equals(nextState) ? now : null);
        subscription.setCancelledAt(("CANCELLED".equals(nextState) || "EXPIRED".equals(nextState)) ? now : null);
        subscription.setNotes(normalizeOptionalText(request.notes(), 2000));
        subscription.setModifiedAt(now);
        subscription.setModifiedBy(effectiveActor);
        TenantSubscription saved = tenantSubscriptionRepository.save(subscription);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("beforePlanCode", beforePlan.getPlanCode());
        metadata.put("afterPlanCode", nextPlan.getPlanCode());
        metadata.put("beforeSubscriptionState", beforeState);
        metadata.put("afterSubscriptionState", saved.getSubscriptionState());
        metadata.put("currentPeriodStart", saved.getCurrentPeriodStart());
        metadata.put("currentPeriodEnd", saved.getCurrentPeriodEnd());
        metadata.put("graceEndsAt", saved.getGraceEndsAt());
        recordAudit(
                "TENANT_SUBSCRIPTION_UPDATED",
                "UPDATE",
                resolved.tenant().getTenantId(),
                effectiveActor,
                resolved.tenant().getId(),
                metadata
        );

        return toSubscriptionResponse(new ResolvedSubscription(
                resolved.tenant(),
                saved,
                nextPlan,
                loadActiveFeatureOverrides(resolved.tenant().getId())
        ));
    }

    private TenantFeatureOverrideResponse upsertFeatureOverrideBlocking(Long tenantMasterId,
                                                                       String featureKey,
                                                                       String actor,
                                                                       TenantFeatureOverrideRequest request) {
        Tenant tenant = requireActiveTenantById(tenantMasterId);
        String normalizedFeatureKey = normalizeFeatureKey(featureKey);
        if (request.enabled() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "enabled is required");
        }

        OffsetDateTime now = OffsetDateTime.now();
        String effectiveActor = normalizeActor(actor);
        TenantFeatureOverride override = featureOverrideRepository.findByTenantMasterIdAndFeatureKey(tenantMasterId, normalizedFeatureKey)
                .orElseGet(TenantFeatureOverride::new);
        boolean existing = override.getId() != null;

        override.setTenantMasterId(tenantMasterId);
        override.setFeatureKey(normalizedFeatureKey);
        override.setEnabled(request.enabled());
        override.setExpiresAt(request.expiresAt());
        override.setReason(normalizeOptionalText(request.reason(), 1000));
        if (!existing) {
            override.setCreatedAt(now);
            override.setCreatedBy(effectiveActor);
        }
        override.setModifiedAt(now);
        override.setModifiedBy(effectiveActor);
        TenantFeatureOverride saved = featureOverrideRepository.save(override);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("featureKey", saved.getFeatureKey());
        metadata.put("enabled", saved.isEnabled());
        metadata.put("expiresAt", saved.getExpiresAt());
        metadata.put("reason", saved.getReason());
        recordAudit(
                "TENANT_FEATURE_OVERRIDE_UPDATED",
                existing ? "UPDATE" : "CREATE",
                tenant.getTenantId(),
                effectiveActor,
                tenant.getId(),
                metadata
        );
        return toFeatureResponse(saved);
    }

    private TenantSubscription createDefaultSubscription(Tenant tenant, String actor) {
        SubscriptionPlan defaultPlan = subscriptionPlanRepository.findActiveByPlanCode(DEFAULT_PLAN_CODE)
                .orElseGet(() -> subscriptionPlanRepository.findAllActive().stream().findFirst()
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "No active subscription plan configured")));
        OffsetDateTime now = OffsetDateTime.now();
        TenantSubscription subscription = new TenantSubscription();
        subscription.setTenantMasterId(tenant.getId());
        subscription.setSubscriptionPlanId(defaultPlan.getId());
        subscription.setSubscriptionState("TRIALING");
        subscription.setStartedAt(now);
        subscription.setCurrentPeriodStart(now);
        subscription.setCurrentPeriodEnd(now.plusDays(DEFAULT_TRIAL_DAYS));
        subscription.setGraceEndsAt(now.plusDays(DEFAULT_TRIAL_DAYS + DEFAULT_GRACE_DAYS));
        subscription.setNotes("Auto-provisioned trial subscription");
        subscription.setCreatedAt(now);
        subscription.setCreatedBy(actor);
        subscription.setModifiedAt(now);
        subscription.setModifiedBy(actor);
        TenantSubscription saved = tenantSubscriptionRepository.save(subscription);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("planCode", defaultPlan.getPlanCode());
        metadata.put("subscriptionState", saved.getSubscriptionState());
        metadata.put("currentPeriodEnd", saved.getCurrentPeriodEnd());
        metadata.put("graceEndsAt", saved.getGraceEndsAt());
        recordAudit(
                "TENANT_SUBSCRIPTION_PROVISIONED",
                "CREATE",
                tenant.getTenantId(),
                actor,
                tenant.getId(),
                metadata
        );
        return saved;
    }

    private Map<String, Boolean> loadActiveFeatureOverrides(Long tenantMasterId) {
        OffsetDateTime now = OffsetDateTime.now();
        Map<String, Boolean> overrides = new LinkedHashMap<>();
        for (TenantFeatureOverride override : featureOverrideRepository.findByTenantMasterId(tenantMasterId)) {
            if (override.getExpiresAt() != null && !override.getExpiresAt().isAfter(now)) {
                continue;
            }
            overrides.put(normalizeFeatureKey(override.getFeatureKey()), override.isEnabled());
        }
        return overrides;
    }

    private Tenant requireActiveTenantById(Long tenantMasterId) {
        Tenant tenant = tenantRepository.findById(tenantMasterId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found"));
        if (tenant.isDeleted()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found");
        }
        return tenant;
    }

    private Tenant requireActiveTenantByCode(String tenantCode) {
        String normalizedTenantCode = normalizeTenantCode(tenantCode);
        return tenantRepository.findActiveByTenantId(normalizedTenantCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found"));
    }

    private TenantSubscriptionResponse toSubscriptionResponse(ResolvedSubscription resolved) {
        List<TenantFeatureOverrideResponse> featureOverrides = featureOverrideRepository.findByTenantMasterId(resolved.tenant().getId())
                .stream()
                .map(this::toFeatureResponse)
                .toList();
        return new TenantSubscriptionResponse(
                resolved.tenant().getId(),
                resolved.tenant().getTenantId(),
                resolved.plan().getPlanCode(),
                resolved.plan().getPlanName(),
                resolved.subscription().getSubscriptionState(),
                resolved.plan().getMaxActiveDevices(),
                resolved.plan().getMaxTenantUsers(),
                resolved.plan().getMaxMonthlyPayloads(),
                resolved.plan().getDataRetentionDays(),
                isFeatureEnabled(FEATURE_PREMIUM_REPORTING, resolved),
                isFeatureEnabled(FEATURE_ADVANCED_CONTROLS, resolved),
                resolved.subscription().getCurrentPeriodStart(),
                resolved.subscription().getCurrentPeriodEnd(),
                resolved.subscription().getGraceEndsAt(),
                resolved.subscription().getNotes(),
                featureOverrides
        );
    }

    private SubscriptionPlanResponse toPlanResponse(SubscriptionPlan plan) {
        return new SubscriptionPlanResponse(
                plan.getPlanCode(),
                plan.getPlanName(),
                plan.getDescription(),
                plan.getMaxActiveDevices(),
                plan.getMaxTenantUsers(),
                plan.getMaxMonthlyPayloads(),
                plan.getDataRetentionDays(),
                plan.isPremiumReportingEnabled(),
                plan.isAdvancedControlsEnabled()
        );
    }

    private SubscriptionPlanAdminResponse toAdminPlanResponse(SubscriptionPlan plan) {
        return new SubscriptionPlanAdminResponse(
                plan.getId(),
                plan.getPlanCode(),
                plan.getPlanName(),
                plan.getDescription(),
                plan.getMaxActiveDevices(),
                plan.getMaxTenantUsers(),
                plan.getMaxMonthlyPayloads(),
                plan.getDataRetentionDays(),
                plan.isPremiumReportingEnabled(),
                plan.isAdvancedControlsEnabled(),
                plan.getStatus(),
                plan.getCreatedAt(),
                plan.getModifiedAt()
        );
    }

    private TenantFeatureOverrideResponse toFeatureResponse(TenantFeatureOverride override) {
        return new TenantFeatureOverrideResponse(
                normalizeFeatureKey(override.getFeatureKey()),
                override.isEnabled(),
                override.getExpiresAt(),
                override.getReason()
        );
    }

    public boolean isFeatureEnabled(String featureKey, ResolvedSubscription resolved) {
        String normalizedFeatureKey = normalizeFeatureKey(featureKey);
        Boolean override = resolved.featureOverrides().get(normalizedFeatureKey);
        if (override != null) {
            return override;
        }
        return switch (normalizedFeatureKey) {
            case FEATURE_PREMIUM_REPORTING -> resolved.plan().isPremiumReportingEnabled();
            case FEATURE_ADVANCED_CONTROLS -> resolved.plan().isAdvancedControlsEnabled();
            default -> false;
        };
    }

    private String normalizeTenantCode(String tenantCode) {
        String normalized = normalizeOptionalText(tenantCode, 64);
        if (normalized == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "tenant_id is required");
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    private String normalizePlanCode(String planCode) {
        String normalized = normalizeOptionalText(planCode, 64);
        if (normalized == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "plan_code is required");
        }
        String upper = normalized.toUpperCase(Locale.ROOT);
        if (!PLAN_CODE_PATTERN.matcher(upper).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "plan_code is invalid");
        }
        return upper;
    }

    private String normalizeManagedPlanCode(String planCode) {
        return normalizePlanCode(planCode);
    }

    private String normalizeSubscriptionState(String value) {
        String normalized = normalizeOptionalText(value, 64);
        if (normalized == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "subscription_state is required");
        }
        String upper = normalized.toUpperCase(Locale.ROOT);
        if (!List.of("TRIALING", "ACTIVE", "GRACE", "PAST_DUE", "SUSPENDED", "CANCELLED", "EXPIRED").contains(upper)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "subscription_state is invalid");
        }
        return upper;
    }

    private String normalizeFeatureKey(String value) {
        String normalized = normalizeOptionalText(value, 64);
        if (normalized == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "feature_key is required");
        }
        String upper = normalized.toUpperCase(Locale.ROOT);
        if (!List.of(FEATURE_PREMIUM_REPORTING, FEATURE_ADVANCED_CONTROLS).contains(upper)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "feature_key is invalid");
        }
        return upper;
    }

    private String normalizePlanName(String value) {
        String normalized = normalizeOptionalText(value, 160);
        if (normalized == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "plan_name is required");
        }
        return normalized;
    }

    private String normalizePlanStatus(String value) {
        String normalized = normalizeOptionalText(value, 32);
        String effective = normalized == null ? "ACTIVE" : normalized.toUpperCase(Locale.ROOT);
        if (!List.of("ACTIVE", "INACTIVE").contains(effective)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status is invalid");
        }
        return effective;
    }

    private int requirePositiveInteger(Integer value, String fieldName) {
        if (value == null || value < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " must be >= 1");
        }
        return value;
    }

    private long requirePositiveLong(Long value, String fieldName) {
        if (value == null || value < 1L) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " must be >= 1");
        }
        return value;
    }

    private void applyPlanFields(SubscriptionPlan target, SubscriptionPlanUpsertRequest request, boolean creating) {
        if (target == null || request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "subscription plan request is required");
        }
        target.setPlanName(normalizePlanName(request.planName()));
        target.setDescription(normalizeOptionalText(request.description(), 2000));
        target.setMaxActiveDevices(requirePositiveInteger(request.maxActiveDevices(), "max_active_devices"));
        target.setMaxTenantUsers(requirePositiveInteger(request.maxTenantUsers(), "max_tenant_users"));
        target.setMaxMonthlyPayloads(requirePositiveLong(request.maxMonthlyPayloads(), "max_monthly_payloads"));
        target.setDataRetentionDays(requirePositiveInteger(request.dataRetentionDays(), "data_retention_days"));
        target.setPremiumReportingEnabled(Boolean.TRUE.equals(request.premiumReportingEnabled()));
        target.setAdvancedControlsEnabled(Boolean.TRUE.equals(request.advancedControlsEnabled()));
        target.setStatus(normalizePlanStatus(request.status()));
        if (creating) {
            target.setDeleted(false);
        }
    }

    private SubscriptionPlan requireManagedPlan(Long planId) {
        if (planId == null || planId <= 0L) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "planId is required");
        }
        return subscriptionPlanRepository.findAvailableById(planId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Subscription plan not found"));
    }

    private void guardAtLeastOneActivePlan(SubscriptionPlan plan, String previousStatus) {
        if (plan == null) {
            return;
        }
        boolean wasActive = "ACTIVE".equalsIgnoreCase(previousStatus);
        boolean becomesInactive = !"ACTIVE".equalsIgnoreCase(plan.getStatus());
        if (!wasActive || !becomesInactive) {
            return;
        }
        long activePlans = subscriptionPlanRepository.findAllActive().size();
        if (activePlans <= 1L) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "At least one active subscription plan is required");
        }
    }

    private String normalizeOptionalText(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }

    private String normalizeActor(String actor) {
        String normalized = normalizeOptionalText(actor, 128);
        return normalized == null ? "system" : normalized;
    }

    private void recordAudit(String eventType,
                             String action,
                             String tenantId,
                             String actor,
                             Long tenantMasterId,
                             Map<String, Object> metadata) {
        auditEventService.recordBestEffort(
                "SUBSCRIPTION",
                eventType,
                action,
                tenantId,
                actor,
                "TENANT_SUBSCRIPTION",
                tenantMasterId == null ? null : String.valueOf(tenantMasterId),
                "SUCCESS",
                metadata
        );
    }

    private void recordPlanAudit(String eventType,
                                 String action,
                                 String actor,
                                 SubscriptionPlan plan,
                                 Map<String, Object> metadata) {
        auditEventService.recordBestEffort(
                "SUBSCRIPTION",
                eventType,
                action,
                null,
                actor,
                "SUBSCRIPTION_PLAN",
                plan == null || plan.getId() == null ? null : String.valueOf(plan.getId()),
                "SUCCESS",
                metadata
        );
    }

}
