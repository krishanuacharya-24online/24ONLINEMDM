package com.e24online.mdm.web;

import com.e24online.mdm.domain.DeviceEnrollment;
import com.e24online.mdm.records.CreateSetupKeyRequest;
import com.e24online.mdm.records.DeEnrollRequest;
import com.e24online.mdm.records.devices.DeviceTokenRotation;
import com.e24online.mdm.records.SetupKeyIssue;
import com.e24online.mdm.records.user.UserPrincipal;
import com.e24online.mdm.service.enrollment.DeviceEnrollmentService;
import com.e24online.mdm.web.security.AuthenticatedRequestContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceEnrollmentControllerTest {

    @Mock
    private DeviceEnrollmentService enrollmentService;

    @Mock
    private AuthenticatedRequestContext requestContext;

    @Mock
    private Authentication authentication;

    private DeviceEnrollmentController controller;

    @BeforeEach
    void setUp() {
        controller = new DeviceEnrollmentController(enrollmentService, requestContext);
    }

    @Test
    void createSetupKey_tenantAdmin_delegatesWithRequestedTarget() {
        UserPrincipal principal = new UserPrincipal(11L, "admin", "TENANT_ADMIN", 1L);
        SetupKeyIssue issue = new SetupKeyIssue(
                1L, "ABC-DEF-GHI-JKL", "ABC...JKL", OffsetDateTime.now().plusHours(1), 5, 22L, 11L
        );
        when(requestContext.requireUserPrincipal(authentication)).thenReturn(principal);
        when(requestContext.resolveActor(authentication)).thenReturn("admin");
        when(requestContext.resolveTenantId(authentication, "tenant-a")).thenReturn(Mono.just("tenant-a"));
        when(enrollmentService.createSetupKeyAsync("tenant-a", 11L, 22L, "admin", 5, 60))
                .thenReturn(Mono.just(issue));

        CreateSetupKeyRequest body = new CreateSetupKeyRequest(5, 22L, 60);
        SetupKeyIssue response = controller
                .createSetupKey("tenant-a", authentication, Mono.just(body))
                .block();

        assertNotNull(response);
        assertEquals(1L, response.setupKeyId());
    }

    @Test
    void createSetupKey_tenantUserDefaultsTargetToSelf() {
        UserPrincipal principal = new UserPrincipal(33L, "user", "TENANT_USER", 1L);
        SetupKeyIssue issue = new SetupKeyIssue(
                2L, "ABC-DEF-GHI-JKL", "ABC...JKL", OffsetDateTime.now().plusHours(1), 1, 33L, 33L
        );
        when(requestContext.requireUserPrincipal(authentication)).thenReturn(principal);
        when(requestContext.resolveActor(authentication)).thenReturn("user");
        when(requestContext.resolveTenantId(authentication, null)).thenReturn(Mono.just("tenant-a"));
        when(enrollmentService.createSetupKeyAsync("tenant-a", 33L, 33L, "user", 1, 30))
                .thenReturn(Mono.just(issue));

        CreateSetupKeyRequest body = new CreateSetupKeyRequest(1, null, 30);
        SetupKeyIssue response = controller
                .createSetupKey(null, authentication, Mono.just(body))
                .block();

        assertNotNull(response);
        assertEquals(33L, response.targetUserId());
    }

    @Test
    void createSetupKey_tenantUserCannotIssueForAnotherUser() {
        UserPrincipal principal = new UserPrincipal(33L, "user", "TENANT_USER", 1L);
        when(requestContext.requireUserPrincipal(authentication)).thenReturn(principal);
        when(requestContext.resolveActor(authentication)).thenReturn("user");
        when(requestContext.resolveTenantId(authentication, null)).thenReturn(Mono.just("tenant-a"));

        CreateSetupKeyRequest body = new CreateSetupKeyRequest(1, 44L, 30);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> controller
                .createSetupKey(null, authentication, Mono.just(body))
                .block());

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void listEnrollments_tenantUserCannotQueryOtherOwner() {
        UserPrincipal principal = new UserPrincipal(90L, "user", "TENANT_USER", 1L);
        when(requestContext.requireUserPrincipal(authentication)).thenReturn(principal);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> controller
                .listEnrollments(null, authentication, "ACTIVE", 91L, 0, 25)
                .collectList()
                .block());

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void listEnrollments_tenantAdminDelegates() {
        UserPrincipal principal = new UserPrincipal(10L, "admin", "TENANT_ADMIN", 1L);
        when(requestContext.requireUserPrincipal(authentication)).thenReturn(principal);
        when(requestContext.resolveTenantId(authentication, "tenant-a")).thenReturn(Mono.just("tenant-a"));
        when(enrollmentService.listEnrollmentsAsync("tenant-a", "ACTIVE", 70L, 1, 25))
                .thenReturn(Flux.just(new DeviceEnrollment()));

        List<DeviceEnrollment> result = controller
                .listEnrollments("tenant-a", authentication, "ACTIVE", 70L, 1, 25)
                .collectList()
                .block();

        assertNotNull(result);
        assertEquals(1, result.size());
    }

    @Test
    void getEnrollment_deEnroll_rotateToken_tenantUserScopedToSelf() {
        UserPrincipal principal = new UserPrincipal(55L, "user", "TENANT_USER", 1L);
        when(requestContext.requireUserPrincipal(authentication)).thenReturn(principal);
        when(requestContext.resolveTenantId(authentication, "tenant-a")).thenReturn(Mono.just("tenant-a"));
        when(requestContext.resolveActor(authentication)).thenReturn("user");

        DeviceEnrollment enrollment = new DeviceEnrollment();
        when(enrollmentService.getEnrollmentAsync("tenant-a", 5L, 55L)).thenReturn(Mono.just(enrollment));
        when(enrollmentService.deEnrollAsync("tenant-a", "user", 55L, 5L, "retired")).thenReturn(Mono.just(enrollment));
        when(enrollmentService.rotateDeviceTokenAsync("tenant-a", "user", 55L, 5L))
                .thenReturn(Mono.just(new DeviceTokenRotation(
                        5L,
                        "ENR-1",
                        "token",
                        "tok...en",
                        OffsetDateTime.now().plusDays(7)
                )));

        DeviceEnrollment getResponse = controller.getEnrollment("tenant-a", authentication, 5L).block();
        assertNotNull(getResponse);

        DeviceEnrollment deEnrollResponse = controller
                .deEnroll("tenant-a", authentication, 5L, Mono.just(new DeEnrollRequest("retired")))
                .block();
        assertNotNull(deEnrollResponse);

        DeviceTokenRotation rotation = controller
                .rotateDeviceToken("tenant-a", authentication, 5L)
                .block();
        assertNotNull(rotation);
        assertEquals(5L, rotation.enrollmentId());

        verify(enrollmentService).getEnrollmentAsync("tenant-a", 5L, 55L);
        verify(enrollmentService).deEnrollAsync("tenant-a", "user", 55L, 5L, "retired");
        verify(enrollmentService).rotateDeviceTokenAsync(eq("tenant-a"), eq("user"), eq(55L), eq(5L));
    }
}

