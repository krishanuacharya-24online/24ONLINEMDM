package com.e24online.mdm.service;

import com.e24online.mdm.domain.SubscriptionPlan;
import com.e24online.mdm.domain.Tenant;
import com.e24online.mdm.domain.TenantSubscription;
import com.e24online.mdm.domain.TenantUsageSnapshot;
import com.e24online.mdm.records.tenant.TenantUsageResponse;
import com.e24online.mdm.repository.AuthUserRepository;
import com.e24online.mdm.repository.DeviceEnrollmentRepository;
import com.e24online.mdm.repository.DevicePosturePayloadRepository;
import com.e24online.mdm.repository.TenantUsageSnapshotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantEntitlementServiceTest {

    @Mock
    private TenantSubscriptionService tenantSubscriptionService;
    @Mock
    private AuthUserRepository authUserRepository;
    @Mock
    private DeviceEnrollmentRepository deviceEnrollmentRepository;
    @Mock
    private DevicePosturePayloadRepository payloadRepository;
    @Mock
    private TenantUsageSnapshotRepository usageSnapshotRepository;

    private TenantEntitlementService service;

    @BeforeEach
    void setUp() {
        service = new TenantEntitlementService(
                tenantSubscriptionService,
                authUserRepository,
                deviceEnrollmentRepository,
                payloadRepository,
                usageSnapshotRepository,
                new BlockingDb(Schedulers.immediate())
        );
        lenient().when(usageSnapshotRepository.save(any(TenantUsageSnapshot.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void assertCanIngestPayload_rejectsWhenMonthlyLimitReached() {
        when(tenantSubscriptionService.loadResolvedSubscriptionByTenantCode("tenant-a"))
                .thenReturn(resolvedSubscription(10, 10, 5L));
        when(usageSnapshotRepository.findByTenantAndUsageMonth(any(), any())).thenReturn(Optional.empty());
        when(payloadRepository.countReceivedInWindow(any(), any(), any())).thenReturn(5L);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                service.assertCanIngestPayload("tenant-a"));

        assertEquals(429, ex.getStatusCode().value());
    }

    @Test
    void getTenantUsage_returnsCurrentCountsAndFlags() {
        when(tenantSubscriptionService.loadResolvedSubscription(1L)).thenReturn(resolvedSubscription(2, 1, 10L));
        when(authUserRepository.countActiveByTenantId(1L)).thenReturn(2L);
        when(deviceEnrollmentRepository.countActiveByTenant("tenant-a")).thenReturn(3L);
        when(usageSnapshotRepository.findByTenantAndUsageMonth(any(), any())).thenReturn(Optional.empty());
        when(payloadRepository.countReceivedInWindow(any(), any(), any())).thenReturn(5L);

        TenantUsageResponse response = service.getTenantUsage(1L).block();

        assertNotNull(response);
        assertEquals(LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1), response.usageMonth());
        assertEquals(3L, response.activeDeviceCount());
        assertEquals(2L, response.activeUserCount());
        assertEquals(5L, response.posturePayloadCount());
        assertEquals(true, response.devicesOverLimit());
        assertEquals(true, response.usersOverLimit());
        assertEquals(false, response.payloadsOverLimit());
    }

    @Test
    void assertCanAccessPremiumReporting_rejectsWhenFeatureDisabled() {
        TenantSubscriptionService.ResolvedSubscription resolved = resolvedSubscription(10, 10, 50L);
        when(tenantSubscriptionService.loadResolvedSubscriptionByTenantCode("tenant-a")).thenReturn(resolved);
        when(tenantSubscriptionService.isFeatureEnabled(TenantSubscriptionService.FEATURE_PREMIUM_REPORTING, resolved)).thenReturn(false);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                service.assertCanAccessPremiumReporting("tenant-a"));

        assertEquals(403, ex.getStatusCode().value());
    }

    @Test
    void assertCanAccessPremiumReporting_acceptsWhenFeatureEnabled() {
        TenantSubscriptionService.ResolvedSubscription resolved = resolvedSubscription(10, 10, 50L);
        when(tenantSubscriptionService.loadResolvedSubscriptionByTenantCode("tenant-a")).thenReturn(resolved);
        when(tenantSubscriptionService.isFeatureEnabled(TenantSubscriptionService.FEATURE_PREMIUM_REPORTING, resolved)).thenReturn(true);

        service.assertCanAccessPremiumReporting("tenant-a");
    }

    private TenantSubscriptionService.ResolvedSubscription resolvedSubscription(int maxActiveDevices,
                                                                               int maxTenantUsers,
                                                                               long maxMonthlyPayloads) {
        Tenant tenant = new Tenant();
        tenant.setId(1L);
        tenant.setTenantId("tenant-a");
        tenant.setStatus("ACTIVE");
        tenant.setDeleted(false);

        TenantSubscription subscription = new TenantSubscription();
        subscription.setId(2L);
        subscription.setTenantMasterId(1L);
        subscription.setSubscriptionState("ACTIVE");
        subscription.setCurrentPeriodStart(OffsetDateTime.now().minusDays(1));

        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setId(3L);
        plan.setPlanCode("STANDARD");
        plan.setPlanName("Standard");
        plan.setMaxActiveDevices(maxActiveDevices);
        plan.setMaxTenantUsers(maxTenantUsers);
        plan.setMaxMonthlyPayloads(maxMonthlyPayloads);
        plan.setDataRetentionDays(30);
        plan.setStatus("ACTIVE");
        plan.setDeleted(false);

        return new TenantSubscriptionService.ResolvedSubscription(
                tenant,
                subscription,
                plan,
                Map.of()
        );
    }
}
