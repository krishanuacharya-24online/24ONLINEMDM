package com.e24online.mdm.web;

import com.e24online.mdm.repository.DeviceTrustProfileRepository;
import com.e24online.mdm.repository.PostureEvaluationRemediationRepository;
import com.e24online.mdm.service.BlockingDb;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;
import reactor.core.scheduler.Schedulers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UiControllerTest {

    @Mock
    private DeviceTrustProfileRepository profileRepository;

    @Mock
    private PostureEvaluationRemediationRepository remediationRepository;

    private UiController controller;

    @BeforeEach
    void setUp() {
        controller = new UiController(
                profileRepository,
                remediationRepository,
                new BlockingDb(Schedulers.immediate())
        );
    }

    @Test
    void overview_populatesDashboardSummary() {
        when(profileRepository.countActive()).thenReturn(10L);
        when(profileRepository.countTrusted()).thenReturn(7L);
        when(profileRepository.countHighRisk()).thenReturn(2L);
        when(remediationRepository.countOpenRemediations()).thenReturn(3L);
        Model model = new ExtendedModelMap();

        String view = controller.overview(model).block();

        assertEquals("overview", view);
        assertEquals("overview", model.getAttribute("activePage"));
        assertEquals(10L, model.getAttribute("totalDevices"));
        assertEquals(7L, model.getAttribute("trustedDevices"));
        assertEquals(2L, model.getAttribute("highRiskDevices"));
        assertEquals(7L, model.getAttribute("healthyDevices"));
        assertEquals(3L, model.getAttribute("openRemediations"));
        assertEquals("MDM Overview", model.getAttribute("title"));
    }

    @Test
    void basePages_setExpectedTitles() {
        Model devicesModel = new ExtendedModelMap();
        assertEquals("devices", controller.devices(devicesModel).block());
        assertEquals("devices", devicesModel.getAttribute("activePage"));
        assertEquals("Devices", devicesModel.getAttribute("title"));

        Model enrollmentsModel = new ExtendedModelMap();
        assertEquals("enrollments", controller.enrollments(enrollmentsModel).block());
        assertEquals("enrollments", enrollmentsModel.getAttribute("activePage"));
        assertEquals("Enrollments", enrollmentsModel.getAttribute("title"));

        Model payloadsModel = new ExtendedModelMap();
        assertEquals("payloads", controller.payloads(payloadsModel).block());
        assertEquals("payloads", payloadsModel.getAttribute("activePage"));
        assertEquals("Payloads", payloadsModel.getAttribute("title"));

        Model usersModel = new ExtendedModelMap();
        assertEquals("users", controller.users(usersModel).block());
        assertEquals("users", usersModel.getAttribute("activePage"));
        assertEquals("Users", usersModel.getAttribute("title"));

        Model reportsModel = new ExtendedModelMap();
        assertEquals("reports", controller.reports(reportsModel).block());
        assertEquals("reports", reportsModel.getAttribute("activePage"));
        assertEquals("Reports", reportsModel.getAttribute("title"));
    }

    @Test
    void policyPages_setExpectedPolicyContext() {
        Model systemRulesModel = new ExtendedModelMap();
        assertEquals("policies_system_rules", controller.policiesSystemRules(systemRulesModel).block());
        assertEquals("policies", systemRulesModel.getAttribute("activePage"));
        assertEquals("system-rules", systemRulesModel.getAttribute("activePolicy"));

        Model conditionsModel = new ExtendedModelMap();
        assertEquals("policies_system_rule_conditions", controller.policiesSystemRuleConditions(9L, conditionsModel).block());
        assertEquals(9L, conditionsModel.getAttribute("ruleId"));

        Model rejectAppsModel = new ExtendedModelMap();
        assertEquals("policies_reject_apps", controller.policiesRejectApps(rejectAppsModel).block());
        assertEquals("reject-apps", rejectAppsModel.getAttribute("activePolicy"));

        Model scoreModel = new ExtendedModelMap();
        assertEquals("policies_trust_score_policies", controller.policiesTrustScorePolicies(scoreModel).block());
        assertEquals("trust-score-policies", scoreModel.getAttribute("activePolicy"));

        Model decisionModel = new ExtendedModelMap();
        assertEquals("policies_trust_decision_policies", controller.policiesTrustDecisionPolicies(decisionModel).block());
        assertEquals("trust-decision-policies", decisionModel.getAttribute("activePolicy"));

        Model remediationModel = new ExtendedModelMap();
        assertEquals("policies_remediation_rules", controller.policiesRemediationRules(remediationModel).block());
        assertEquals("remediation-rules", remediationModel.getAttribute("activePolicy"));

        Model mappingModel = new ExtendedModelMap();
        assertEquals("policies_rule_remediation_mappings", controller.policiesRuleRemediationMappings(mappingModel).block());
        assertEquals("rule-remediation-mappings", mappingModel.getAttribute("activePolicy"));

        Model auditModel = new ExtendedModelMap();
        assertEquals("policies_audit_trail", controller.policiesAuditTrail(auditModel).block());
        assertEquals("audit-trail", auditModel.getAttribute("activePolicy"));
    }

    @Test
    void adminPages_setExpectedViews() {
        Model catalogModel = new ExtendedModelMap();
        assertEquals("catalog_applications", controller.catalogApplications(catalogModel).block());
        assertEquals("catalog", catalogModel.getAttribute("activePage"));

        Model lookupModel = new ExtendedModelMap();
        assertEquals("lookups_admin", controller.lookups(lookupModel).block());
        assertEquals("lookups", lookupModel.getAttribute("activePage"));

        Model tenantsModel = new ExtendedModelMap();
        assertEquals("tenants", controller.tenants(tenantsModel).block());
        assertEquals("tenants", tenantsModel.getAttribute("activePage"));

        Model lifecycleModel = new ExtendedModelMap();
        assertEquals("os_lifecycle", controller.osLifecycle(lifecycleModel).block());
        assertEquals("os-lifecycle", lifecycleModel.getAttribute("activePage"));

        Model passwordModel = new ExtendedModelMap();
        assertEquals("change_password", controller.changePassword(passwordModel).block());
        assertEquals("account", passwordModel.getAttribute("activePage"));
        assertEquals("Change password", passwordModel.getAttribute("title"));
    }
}
