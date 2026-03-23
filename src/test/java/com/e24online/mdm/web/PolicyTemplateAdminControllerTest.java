package com.e24online.mdm.web;

import com.e24online.mdm.records.PolicyTemplateApplyReport;
import com.e24online.mdm.service.PolicyTemplateMaintenanceService;
import com.e24online.mdm.web.security.AuthenticatedRequestContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PolicyTemplateAdminControllerTest {

    @Mock
    private PolicyTemplateMaintenanceService policyTemplateMaintenanceService;

    @Mock
    private AuthenticatedRequestContext requestContext;

    @Mock
    private Authentication authentication;

    private PolicyTemplateAdminController controller;

    @BeforeEach
    void setUp() {
        controller = new PolicyTemplateAdminController(policyTemplateMaintenanceService, requestContext);
    }

    @Test
    void applyProductionPack_usesResolvedActorAndFlags() {
        PolicyTemplateApplyReport report = new PolicyTemplateApplyReport(
                "production-policy-pack-v1",
                "admin",
                true,
                false,
                0,
                1,
                1,
                1,
                1,
                1,
                1,
                1,
                3,
                3,
                2,
                7,
                4,
                6,
                8,
                OffsetDateTime.now()
        );
        when(requestContext.resolveActor(authentication)).thenReturn("admin");
        when(policyTemplateMaintenanceService.applyProductionPack("admin", true, false)).thenReturn(Mono.just(report));

        PolicyTemplateApplyReport response = controller.applyProductionPack(authentication, true, false).block();

        assertNotNull(response);
        assertEquals("production-policy-pack-v1", response.packName());
        verify(requestContext).resolveActor(authentication);
        verify(policyTemplateMaintenanceService).applyProductionPack("admin", true, false);
    }
}
