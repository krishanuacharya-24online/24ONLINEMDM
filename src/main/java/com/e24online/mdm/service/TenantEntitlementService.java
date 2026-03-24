package com.e24online.mdm.service;

import com.e24online.mdm.domain.TenantUsageSnapshot;
import com.e24online.mdm.records.tenant.ResolvedSubscription;
import com.e24online.mdm.records.tenant.TenantUsageResponse;
import com.e24online.mdm.repository.AuthUserRepository;
import com.e24online.mdm.repository.DeviceEnrollmentRepository;
import com.e24online.mdm.repository.DevicePosturePayloadRepository;
import com.e24online.mdm.repository.TenantUsageSnapshotRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Service
public class TenantEntitlementService {

    private final TenantSubscriptionService tenantSubscriptionService;
    private final AuthUserRepository authUserRepository;
    private final DeviceEnrollmentRepository deviceEnrollmentRepository;
    private final DevicePosturePayloadRepository payloadRepository;
    private final TenantUsageSnapshotRepository usageSnapshotRepository;
    private final BlockingDb blockingDb;

    public TenantEntitlementService(TenantSubscriptionService tenantSubscriptionService,
                                    AuthUserRepository authUserRepository,
                                    DeviceEnrollmentRepository deviceEnrollmentRepository,
                                    DevicePosturePayloadRepository payloadRepository,
                                    TenantUsageSnapshotRepository usageSnapshotRepository,
                                    BlockingDb blockingDb) {
        this.tenantSubscriptionService = tenantSubscriptionService;
        this.authUserRepository = authUserRepository;
        this.deviceEnrollmentRepository = deviceEnrollmentRepository;
        this.payloadRepository = payloadRepository;
        this.usageSnapshotRepository = usageSnapshotRepository;
        this.blockingDb = blockingDb;
    }

    public Mono<TenantUsageResponse> getTenantUsage(Long tenantMasterId) {
        return blockingDb.mono(() -> buildTenantUsage(tenantSubscriptionService.loadResolvedSubscription(tenantMasterId)));
    }

    public void assertCanAccessPremiumReporting(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "tenantId is required for premium reporting");
        }
        ResolvedSubscription resolved = tenantSubscriptionService.loadResolvedSubscriptionByTenantCode(tenantId);
        assertSubscriptionAllowsCoreFlow(resolved, "premium reporting");
        if (!tenantSubscriptionService.isFeatureEnabled(TenantSubscriptionService.FEATURE_PREMIUM_REPORTING, resolved)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Current tenant subscription does not include premium reporting");
        }
    }

    public void assertCanCreateActiveTenantUser(Long tenantMasterId) {
        ResolvedSubscription resolved = tenantSubscriptionService.loadResolvedSubscription(tenantMasterId);
        assertSubscriptionAllowsCoreFlow(resolved, "user activation");
        long currentUsers = authUserRepository.countActiveByTenantId(tenantMasterId);
        Integer limit = resolved.plan().getMaxTenantUsers();
        if (limit != null && currentUsers >= limit) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Tenant user limit reached for current subscription");
        }
    }

    public void assertCanEnrollDevice(String tenantId) {
        ResolvedSubscription resolved = tenantSubscriptionService.loadResolvedSubscriptionByTenantCode(tenantId);
        assertSubscriptionAllowsCoreFlow(resolved, "device enrollment");
        long currentDevices = deviceEnrollmentRepository.countActiveByTenant(resolved.tenant().getTenantId());
        Integer limit = resolved.plan().getMaxActiveDevices();
        if (limit != null && currentDevices >= limit) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Active device limit reached for current subscription");
        }
    }

    public void assertCanIngestPayload(String tenantId) {
        ResolvedSubscription resolved = tenantSubscriptionService.loadResolvedSubscriptionByTenantCode(tenantId);
        assertSubscriptionAllowsCoreFlow(resolved, "payload ingest");
        long payloadCount = currentPayloadCount(resolved);
        Long limit = resolved.plan().getMaxMonthlyPayloads();
        if (limit != null && payloadCount >= limit) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Monthly payload limit reached for current subscription");
        }
    }

    public void recordPayloadAccepted(String tenantId, OffsetDateTime receivedAt) {
        ResolvedSubscription resolved = tenantSubscriptionService.loadResolvedSubscriptionByTenantCode(tenantId);
        upsertUsageSnapshot(
                resolved.tenant().getId(),
                usageMonth(receivedAt),
                authUserRepository.countActiveByTenantId(resolved.tenant().getId()),
                deviceEnrollmentRepository.countActiveByTenant(resolved.tenant().getTenantId()),
                currentPayloadCount(resolved)
        );
    }

    public void refreshUsageSnapshotForTenantId(Long tenantMasterId) {
        buildTenantUsage(tenantSubscriptionService.loadResolvedSubscription(tenantMasterId));
    }

    public void refreshUsageSnapshotForTenantCode(String tenantId) {
        buildTenantUsage(tenantSubscriptionService.loadResolvedSubscriptionByTenantCode(tenantId));
    }

    private TenantUsageResponse buildTenantUsage(ResolvedSubscription resolved) {
        LocalDate currentMonth = usageMonth(OffsetDateTime.now(ZoneOffset.UTC));
        long activeUsers = authUserRepository.countActiveByTenantId(resolved.tenant().getId());
        long activeDevices = deviceEnrollmentRepository.countActiveByTenant(resolved.tenant().getTenantId());
        long payloadCount = currentPayloadCount(resolved);
        upsertUsageSnapshot(resolved.tenant().getId(), currentMonth, activeUsers, activeDevices, payloadCount);

        Integer maxActiveDevices = resolved.plan().getMaxActiveDevices();
        Integer maxTenantUsers = resolved.plan().getMaxTenantUsers();
        Long maxMonthlyPayloads = resolved.plan().getMaxMonthlyPayloads();
        return new TenantUsageResponse(
                resolved.tenant().getId(),
                resolved.tenant().getTenantId(),
                currentMonth,
                activeDevices,
                activeUsers,
                payloadCount,
                maxActiveDevices,
                maxTenantUsers,
                maxMonthlyPayloads,
                maxActiveDevices != null && activeDevices > maxActiveDevices,
                maxTenantUsers != null && activeUsers > maxTenantUsers,
                maxMonthlyPayloads != null && payloadCount > maxMonthlyPayloads
        );
    }

    private long currentPayloadCount(ResolvedSubscription resolved) {
        LocalDate currentMonth = usageMonth(OffsetDateTime.now(ZoneOffset.UTC));
        long snapshotCount = usageSnapshotRepository.findByTenantAndUsageMonth(resolved.tenant().getId(), currentMonth)
                .map(TenantUsageSnapshot::getPosturePayloadCount)
                .orElse(0L);
        OffsetDateTime fromInclusive = currentMonth.atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime toExclusive = currentMonth.plusMonths(1).atStartOfDay().atOffset(ZoneOffset.UTC);
        long actualCount = payloadRepository.countReceivedInWindow(
                resolved.tenant().getTenantId(),
                fromInclusive,
                toExclusive
        );
        return Math.max(snapshotCount, actualCount);
    }

    private void upsertUsageSnapshot(Long tenantMasterId,
                                     LocalDate usageMonth,
                                     long activeUsers,
                                     long activeDevices,
                                     long payloadCount) {
        TenantUsageSnapshot snapshot = usageSnapshotRepository.findByTenantAndUsageMonth(tenantMasterId, usageMonth)
                .orElseGet(TenantUsageSnapshot::new);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        boolean existing = snapshot.getId() != null;
        snapshot.setTenantMasterId(tenantMasterId);
        snapshot.setUsageMonth(usageMonth);
        snapshot.setActiveUserCount(Math.toIntExact(activeUsers));
        snapshot.setActiveDeviceCount(Math.toIntExact(activeDevices));
        snapshot.setPosturePayloadCount(payloadCount);
        if (!existing) {
            snapshot.setCreatedAt(now);
            snapshot.setCreatedBy("entitlement-meter");
        }
        snapshot.setModifiedAt(now);
        snapshot.setModifiedBy("entitlement-meter");
        usageSnapshotRepository.save(snapshot);
    }

    private LocalDate usageMonth(OffsetDateTime value) {
        OffsetDateTime effective = value == null ? OffsetDateTime.now(ZoneOffset.UTC) : value.withOffsetSameInstant(ZoneOffset.UTC);
        return effective.toLocalDate().withDayOfMonth(1);
    }

    private void assertSubscriptionAllowsCoreFlow(ResolvedSubscription resolved, String flowName) {
        String state = resolved.subscription().getSubscriptionState();
        if ("SUSPENDED".equalsIgnoreCase(state)
                || "CANCELLED".equalsIgnoreCase(state)
                || "EXPIRED".equalsIgnoreCase(state)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tenant subscription does not allow " + flowName);
        }
    }
}
