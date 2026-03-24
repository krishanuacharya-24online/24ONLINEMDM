package com.e24online.mdm.service;

import com.e24online.mdm.domain.SubscriptionPlan;
import com.e24online.mdm.domain.Tenant;
import com.e24online.mdm.domain.TenantSubscription;
import com.e24online.mdm.records.tenant.SubscriptionPlanAdminResponse;
import com.e24online.mdm.records.tenant.SubscriptionPlanUpsertRequest;
import com.e24online.mdm.records.tenant.TenantSubscriptionResponse;
import com.e24online.mdm.records.tenant.TenantSubscriptionUpsertRequest;
import com.e24online.mdm.repository.SubscriptionPlanRepository;
import com.e24online.mdm.repository.TenantFeatureOverrideRepository;
import com.e24online.mdm.repository.TenantRepository;
import com.e24online.mdm.repository.TenantSubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.scheduler.Schedulers;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantSubscriptionServiceTest {

    @Mock
    private TenantRepository tenantRepository;
    @Mock
    private SubscriptionPlanRepository subscriptionPlanRepository;
    @Mock
    private TenantSubscriptionRepository tenantSubscriptionRepository;
    @Mock
    private TenantFeatureOverrideRepository featureOverrideRepository;
    @Mock
    private AuditEventService auditEventService;

    private TenantSubscriptionService service;

    @BeforeEach
    void setUp() {
        service = new TenantSubscriptionService(
                tenantRepository,
                subscriptionPlanRepository,
                tenantSubscriptionRepository,
                featureOverrideRepository,
                new BlockingDb(Schedulers.immediate()),
                auditEventService
        );
        lenient().when(featureOverrideRepository.findByTenantMasterId(any())).thenReturn(java.util.List.of());
        lenient().when(subscriptionPlanRepository.findAllActive()).thenReturn(List.of(plan(5L, "TRIAL")));
        lenient().when(subscriptionPlanRepository.save(any(SubscriptionPlan.class))).thenAnswer(invocation -> {
            SubscriptionPlan plan = invocation.getArgument(0);
            if (plan.getId() == null) {
                plan.setId(10L);
            }
            return plan;
        });
        lenient().when(tenantSubscriptionRepository.save(any(TenantSubscription.class))).thenAnswer(invocation -> {
            TenantSubscription subscription = invocation.getArgument(0);
            if (subscription.getId() == null) {
                subscription.setId(10L);
            }
            return subscription;
        });
    }

    @Test
    void ensureSubscriptionForTenant_provisionsDefaultTrialSubscription() {
        Tenant tenant = tenant();
        SubscriptionPlan plan = plan(5L, "TRIAL");
        when(tenantSubscriptionRepository.findByTenantMasterId(1L)).thenReturn(Optional.empty());
        when(subscriptionPlanRepository.findActiveByPlanCode("TRIAL")).thenReturn(Optional.of(plan));

        TenantSubscription subscription = service.ensureSubscriptionForTenant(tenant, "admin");

        assertNotNull(subscription);
        assertEquals(1L, subscription.getTenantMasterId());
        assertEquals(5L, subscription.getSubscriptionPlanId());
        assertEquals("TRIALING", subscription.getSubscriptionState());
    }

    @Test
    void upsertSubscription_updatesPlanAndState() {
        Tenant tenant = tenant();
        SubscriptionPlan currentPlan = plan(5L, "TRIAL");
        SubscriptionPlan nextPlan = plan(6L, "STANDARD");
        TenantSubscription existing = new TenantSubscription();
        existing.setId(99L);
        existing.setTenantMasterId(1L);
        existing.setSubscriptionPlanId(5L);
        existing.setSubscriptionState("TRIALING");
        existing.setCurrentPeriodStart(OffsetDateTime.now().minusDays(3));

        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));
        when(tenantSubscriptionRepository.findByTenantMasterId(1L)).thenReturn(Optional.of(existing));
        when(subscriptionPlanRepository.findAvailableById(5L)).thenReturn(Optional.of(currentPlan));
        when(subscriptionPlanRepository.findActiveByPlanCode("STANDARD")).thenReturn(Optional.of(nextPlan));

        TenantSubscriptionResponse response = service.upsertSubscription(
                1L,
                "admin",
                new TenantSubscriptionUpsertRequest(
                        "STANDARD",
                        "ACTIVE",
                        existing.getCurrentPeriodStart(),
                        OffsetDateTime.now().plusDays(30),
                        null,
                        "migrated"
                )
        ).block();

        assertNotNull(response);
        assertEquals("STANDARD", response.planCode());
        assertEquals("ACTIVE", response.subscriptionState());
        assertEquals("migrated", response.notes());
    }

    @Test
    void createPlan_createsActivePlan() {
        when(subscriptionPlanRepository.findAvailableByPlanCode("STANDARD")).thenReturn(Optional.empty());

        SubscriptionPlanAdminResponse response = service.createPlan(
                "admin",
                new SubscriptionPlanUpsertRequest(
                        "STANDARD",
                        "Standard",
                        "Core production plan",
                        50,
                        15,
                        10000L,
                        45,
                        true,
                        false,
                        "ACTIVE"
                )
        ).block();

        assertNotNull(response);
        assertEquals("STANDARD", response.planCode());
        assertEquals("Standard", response.planName());
        assertEquals("ACTIVE", response.status());
    }

    @Test
    void updatePlan_canRetireWithoutBreakingCatalogAccess() {
        SubscriptionPlan existing = plan(5L, "TRIAL");
        existing.setCreatedAt(OffsetDateTime.now().minusDays(5));
        when(subscriptionPlanRepository.findAvailableById(5L)).thenReturn(Optional.of(existing));
        when(subscriptionPlanRepository.findAvailableByPlanCode("TRIAL")).thenReturn(Optional.of(existing));
        when(subscriptionPlanRepository.findAllActive()).thenReturn(List.of(existing, plan(6L, "STANDARD")));

        SubscriptionPlanAdminResponse response = service.updatePlan(
                5L,
                "admin",
                new SubscriptionPlanUpsertRequest(
                        "TRIAL",
                        "Trial Plan",
                        "Retired but still readable",
                        25,
                        10,
                        5000L,
                        30,
                        false,
                        false,
                        "INACTIVE"
                )
        ).block();

        assertNotNull(response);
        assertEquals("INACTIVE", response.status());
    }

    @Test
    void retirePlan_rejectsLastActivePlan() {
        SubscriptionPlan existing = plan(5L, "TRIAL");
        when(subscriptionPlanRepository.findAvailableById(5L)).thenReturn(Optional.of(existing));
        when(subscriptionPlanRepository.findAllActive()).thenReturn(List.of(existing));

        ResponseStatusException error = assertThrows(ResponseStatusException.class, () ->
                service.retirePlan(5L, "admin").block()
        );

        assertEquals(409, error.getStatusCode().value());
    }

    @Test
    void loadResolvedSubscription_acceptsInactiveAssignedPlan() {
        Tenant tenant = tenant();
        SubscriptionPlan inactivePlan = plan(5L, "TRIAL");
        inactivePlan.setStatus("INACTIVE");
        TenantSubscription existing = new TenantSubscription();
        existing.setId(99L);
        existing.setTenantMasterId(1L);
        existing.setSubscriptionPlanId(5L);
        existing.setSubscriptionState("ACTIVE");

        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));
        when(tenantSubscriptionRepository.findByTenantMasterId(1L)).thenReturn(Optional.of(existing));
        when(subscriptionPlanRepository.findAvailableById(5L)).thenReturn(Optional.of(inactivePlan));

        TenantSubscriptionResponse response = service.getSubscription(1L).block();

        assertNotNull(response);
        assertEquals("TRIAL", response.planCode());
        assertEquals("TRIAL Plan", response.planName());
    }

    private Tenant tenant() {
        Tenant tenant = new Tenant();
        tenant.setId(1L);
        tenant.setTenantId("tenant-a");
        tenant.setStatus("ACTIVE");
        tenant.setDeleted(false);
        return tenant;
    }

    private SubscriptionPlan plan(Long id, String code) {
        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setId(id);
        plan.setPlanCode(code);
        plan.setPlanName(code + " Plan");
        plan.setMaxActiveDevices(25);
        plan.setMaxTenantUsers(10);
        plan.setMaxMonthlyPayloads(100L);
        plan.setDataRetentionDays(30);
        plan.setStatus("ACTIVE");
        plan.setDeleted(false);
        plan.setCreatedAt(OffsetDateTime.now().minusDays(1));
        plan.setModifiedAt(OffsetDateTime.now());
        return plan;
    }
}
