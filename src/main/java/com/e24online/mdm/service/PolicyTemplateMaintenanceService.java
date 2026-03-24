package com.e24online.mdm.service;

import com.e24online.mdm.domain.PolicyChangeAudit;
import com.e24online.mdm.domain.RejectApplication;
import com.e24online.mdm.domain.RemediationRule;
import com.e24online.mdm.domain.RuleRemediationMapping;
import com.e24online.mdm.domain.SystemInformationRule;
import com.e24online.mdm.domain.SystemInformationRuleCondition;
import com.e24online.mdm.domain.TrustScoreDecisionPolicy;
import com.e24online.mdm.domain.TrustScorePolicy;
import com.e24online.mdm.records.PolicyTemplateApplyReport;
import com.e24online.mdm.records.policy.*;
import com.e24online.mdm.repository.PolicyChangeAuditRepository;
import com.e24online.mdm.repository.RejectApplicationRepository;
import com.e24online.mdm.repository.RemediationRuleRepository;
import com.e24online.mdm.repository.RuleRemediationMappingRepository;
import com.e24online.mdm.repository.SystemInformationRuleConditionRepository;
import com.e24online.mdm.repository.SystemInformationRuleRepository;
import com.e24online.mdm.repository.TrustScoreDecisionPolicyRepository;
import com.e24online.mdm.repository.TrustScorePolicyRepository;
import com.e24online.mdm.service.messaging.PolicyAuditPublisher;
import com.e24online.mdm.web.dto.PolicyAuditMessage;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class PolicyTemplateMaintenanceService {

    static final String PACK_NAME = "production-policy-pack-v2";

    private static final List<String> NON_ANDROID_VIRTUAL_DEVICE_OS_TYPES = List.of(
            "WINDOWS",
            "MACOS",
            "LINUX",
            "CHROMEOS",
            "FREEBSD",
            "OPENBSD"
    );

    private static final List<TemplateRemediation> PRODUCTION_REMEDIATIONS = buildProductionRemediations();

    private static final List<TemplateSystemRule> PRODUCTION_SYSTEM_RULES = buildProductionSystemRules();

    private static final List<TemplateRejectApp> PRODUCTION_REJECT_APPS = List.of(
            new TemplateRejectApp("ANYDESK_WINDOWS", "REMOTE_ADMIN_TOOL", (short) 4, "AnyDesk", "AnyDesk Software GmbH", "AnyDeskSoftwareGmbH.AnyDesk", "WINDOWS", null),
            new TemplateRejectApp("TEAMVIEWER_WINDOWS", "REMOTE_ADMIN_TOOL", (short) 4, "TeamViewer", "TeamViewer Germany GmbH", null, "WINDOWS", null)
    );

    private static final List<TemplateTrustPolicy> PRODUCTION_TRUST_POLICIES = buildProductionTrustPolicies();

    private static final List<TemplateDecisionPolicy> PRODUCTION_DECISION_POLICIES = List.of(
            new TemplateDecisionPolicy("Production: Trusted devices", (short) 80, (short) 100, "ALLOW", false, "Device posture is within the trusted operating range."),
            new TemplateDecisionPolicy("Production: Watch devices", (short) 60, (short) 79, "NOTIFY", false, "Device posture needs attention but does not yet require containment."),
            new TemplateDecisionPolicy("Production: Restricted devices", (short) 40, (short) 59, "QUARANTINE", true, "Device posture requires restricted access until remediation completes."),
            new TemplateDecisionPolicy("Production: Blocked devices", (short) 0, (short) 39, "BLOCK", true, "Device posture is outside the acceptable range and access is blocked.")
    );

    private static final List<TemplateMapping> PRODUCTION_MAPPINGS = buildProductionMappings();

    private final SystemInformationRuleRepository systemRuleRepository;
    private final SystemInformationRuleConditionRepository conditionRepository;
    private final RejectApplicationRepository rejectApplicationRepository;
    private final TrustScorePolicyRepository trustScorePolicyRepository;
    private final TrustScoreDecisionPolicyRepository trustScoreDecisionPolicyRepository;
    private final RemediationRuleRepository remediationRuleRepository;
    private final RuleRemediationMappingRepository ruleRemediationMappingRepository;
    private final PolicyChangeAuditRepository policyChangeAuditRepository;
    private final PolicyAuditPublisher policyAuditPublisher;
    private final AuditEventService auditEventService;
    private final BlockingDb blockingDb;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    public PolicyTemplateMaintenanceService(SystemInformationRuleRepository systemRuleRepository,
                                            SystemInformationRuleConditionRepository conditionRepository,
                                            RejectApplicationRepository rejectApplicationRepository,
                                            TrustScorePolicyRepository trustScorePolicyRepository,
                                            TrustScoreDecisionPolicyRepository trustScoreDecisionPolicyRepository,
                                            RemediationRuleRepository remediationRuleRepository,
                                            RuleRemediationMappingRepository ruleRemediationMappingRepository,
                                            PolicyChangeAuditRepository policyChangeAuditRepository,
                                            PolicyAuditPublisher policyAuditPublisher,
                                            AuditEventService auditEventService,
                                            BlockingDb blockingDb,
                                            TransactionTemplate transactionTemplate,
                                            ObjectMapper objectMapper,
                                            MeterRegistry meterRegistry) {
        this.systemRuleRepository = systemRuleRepository;
        this.conditionRepository = conditionRepository;
        this.rejectApplicationRepository = rejectApplicationRepository;
        this.trustScorePolicyRepository = trustScorePolicyRepository;
        this.trustScoreDecisionPolicyRepository = trustScoreDecisionPolicyRepository;
        this.remediationRuleRepository = remediationRuleRepository;
        this.ruleRemediationMappingRepository = ruleRemediationMappingRepository;
        this.policyChangeAuditRepository = policyChangeAuditRepository;
        this.policyAuditPublisher = policyAuditPublisher;
        this.auditEventService = auditEventService;
        this.blockingDb = blockingDb;
        this.transactionTemplate = transactionTemplate;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
    }

    private static List<TemplateRemediation> buildProductionRemediations() {
        List<TemplateRemediation> remediations = new ArrayList<>();
        remediations.add(new TemplateRemediation(
                "PROD_FIX_ROOT_ACCESS",
                "Remove root access and rescan",
                "Remove root or jailbreak tooling from the device, then collect posture again.",
                "USER_ACTION",
                "ANDROID",
                null,
                (short) 10,
                """
                {"version":1,"title":"Remove root access and rescan","summary":"Remove root or jailbreak tooling from the device, then collect posture again.","steps":["Remove root or jailbreak tooling from the device.","Reboot the device to restore the normal security state.","Open the agent and run posture collection again."],"verification_hint":"Verify the next scan reports root_detected = false.","affected_evidence_ref":["root_detected"]}
                """
        ));
        remediations.add(new TemplateRemediation(
                "PROD_FIX_JAILBREAK_ACCESS",
                "Remove jailbreak access and rescan",
                "Remove jailbreak tooling from the device, then collect posture again.",
                "USER_ACTION",
                "IOS",
                null,
                (short) 15,
                """
                {"version":1,"title":"Remove jailbreak access and rescan","summary":"Remove jailbreak tooling from the device, then collect posture again.","steps":["Remove jailbreak tooling or restore the device to a supported state.","Reboot the device so the integrity check can run cleanly.","Open the agent and run posture collection again."],"verification_hint":"Verify the next scan reports root_detected = false.","affected_evidence_ref":["root_detected"]}
                """
        ));
        remediations.add(new TemplateRemediation(
                "PROD_FIX_USB_DEBUGGING",
                "Turn off USB debugging",
                "Disable USB debugging and run posture collection again.",
                "USER_ACTION",
                "ANDROID",
                null,
                (short) 20,
                """
                {"version":1,"title":"Turn off USB debugging","summary":"Disable USB debugging and run posture collection again.","steps":["Open Developer Options on the device.","Turn off USB debugging.","Reconnect the device only after policy verification completes."],"verification_hint":"Verify the next scan reports usb_debugging_status = false.","affected_evidence_ref":["usb_debugging_status"]}
                """
        ));
        remediations.add(new TemplateRemediation(
                "PROD_FIX_EMULATOR_USAGE",
                "Move to a supported physical device",
                "Use a physical managed device instead of an emulator or virtual machine.",
                "USER_ACTION",
                "ANDROID",
                null,
                (short) 30,
                """
                {"version":1,"title":"Move to a supported physical device","summary":"Use a physical managed device instead of an emulator or virtual machine.","steps":["Stop using the emulated or virtualized device for this workflow.","Move the user to a supported physical device.","Enroll and scan the physical device."],"verification_hint":"Verify the next scan reports running_on_emulator = false.","affected_evidence_ref":["running_on_emulator"]}
                """
        ));
        remediations.add(new TemplateRemediation(
                "PROD_FIX_NON_PHYSICAL_DEVICE",
                "Move the workload to a supported physical device",
                "Use a supported physical managed device instead of an emulator, simulator, or virtual machine, then collect posture again.",
                "USER_ACTION",
                null,
                null,
                (short) 35,
                """
                {"version":1,"title":"Move the workload to a supported physical device","summary":"Use a supported physical managed device instead of an emulator, simulator, or virtual machine, then collect posture again.","steps":["Stop using the emulator, simulator, or virtual machine for the protected workflow.","Move the user or workload to a supported physical managed device.","Run posture collection again on the physical device."],"verification_hint":"Verify the next scan reports running_on_emulator = false.","affected_evidence_ref":["running_on_emulator"]}
                """
        ));
        remediations.add(new TemplateRemediation(
                "PROD_FIX_REMOVE_REMOTE_ADMIN_TOOL",
                "Remove the blocked remote admin tool",
                "Uninstall the blocked remote admin application and run posture collection again.",
                "APP_REMOVAL",
                "WINDOWS",
                null,
                (short) 40,
                """
                {"version":1,"title":"Remove the blocked remote admin tool","summary":"Uninstall the blocked remote admin application and run posture collection again.","steps":["Uninstall the blocked remote admin application from the device.","Confirm the application is no longer listed in installed programs.","Open the agent and run posture collection again."],"verification_hint":"Verify the blocked application no longer appears in the installed application list.","affected_evidence_ref":["installed_applications"]}
                """
        ));
        remediations.add(new TemplateRemediation(
                "PROD_FIX_UPDATE_OS_SUPPORTED",
                "Update the device to a supported OS release",
                "Move the device to a currently supported operating-system release and scan again.",
                "OS_UPDATE",
                null,
                null,
                (short) 50,
                """
                {"version":1,"title":"Update the device to a supported OS release","summary":"Move the device to a currently supported operating-system release and scan again.","steps":["Review the supported OS baseline for the platform.","Apply the latest supported OS release for the device.","Run posture collection again after the update completes."],"verification_hint":"Verify the next scan no longer reports OS_EOL or OS_NOT_TRACKED.","affected_evidence_ref":["os_cycle","os_version"]}
                """
        ));
        remediations.add(new TemplateRemediation(
                "PROD_FIX_REPLACE_UNSUPPORTED_OS",
                "Move the device off the unsupported OS release",
                "Upgrade or replace the device because the current operating-system release is no longer supportable.",
                "OS_UPDATE",
                null,
                null,
                (short) 60,
                """
                {"version":1,"title":"Move the device off the unsupported OS release","summary":"Upgrade or replace the device because the current operating-system release is no longer supportable.","steps":["Review the lifecycle baseline for the current operating-system release.","Upgrade the device to a supported release or replace the device.","Run posture collection again after the change."],"verification_hint":"Verify the next scan no longer reports OS_EEOL.","affected_evidence_ref":["os_cycle","os_version"]}
                """
        ));
        return List.copyOf(remediations);
    }

    private static List<TemplateSystemRule> buildProductionSystemRules() {
        List<TemplateSystemRule> rules = new ArrayList<>();
        rules.add(new TemplateSystemRule(
                "PROD_ROOTED_ANDROID_DEVICE",
                "Rooted Android device",
                "Block devices that report root or jailbreak access.",
                "ANDROID",
                null,
                (short) 5,
                10,
                "BLOCK",
                (short) -60,
                "root_detected",
                "EQ",
                null,
                null,
                Boolean.TRUE
        ));
        rules.add(new TemplateSystemRule(
                "PROD_JAILBROKEN_IOS_DEVICE",
                "Jailbroken iOS device",
                "Block iOS devices that report jailbreak access.",
                "IOS",
                null,
                (short) 5,
                15,
                "BLOCK",
                (short) -60,
                "root_detected",
                "EQ",
                null,
                null,
                Boolean.TRUE
        ));
        rules.add(new TemplateSystemRule(
                "PROD_USB_DEBUGGING_ENABLED",
                "USB debugging enabled",
                "Restrict devices that still have USB debugging enabled.",
                "ANDROID",
                null,
                (short) 4,
                25,
                "QUARANTINE",
                (short) -45,
                "usb_debugging_status",
                "EQ",
                null,
                null,
                Boolean.TRUE
        ));
        rules.add(new TemplateSystemRule(
                "PROD_RUNNING_ON_EMULATOR",
                "Running on emulator",
                "Restrict Android devices that report emulator or virtualization usage.",
                "ANDROID",
                null,
                (short) 4,
                35,
                "QUARANTINE",
                (short) -45,
                "running_on_emulator",
                "EQ",
                null,
                null,
                Boolean.TRUE
        ));

        int priority = 40;
        for (String osType : NON_ANDROID_VIRTUAL_DEVICE_OS_TYPES) {
            rules.add(new TemplateSystemRule(
                    virtualDeviceRuleCode(osType),
                    virtualDeviceRuleTag(osType),
                    "Restrict " + displayOsName(osType) + " devices that report emulator, simulator, or virtualization usage.",
                    osType,
                    null,
                    (short) 4,
                    priority++,
                    "QUARANTINE",
                    (short) -45,
                    "running_on_emulator",
                    "EQ",
                    null,
                    null,
                    Boolean.TRUE
            ));
        }

        return List.copyOf(rules);
    }

    private static List<TemplateTrustPolicy> buildProductionTrustPolicies() {
        List<TemplateTrustPolicy> policies = new ArrayList<>();
        policies.add(new TemplateTrustPolicy("PROD_SCORE_ROOTED_ANDROID_DEVICE", "SYSTEM_RULE", "PROD_ROOTED_ANDROID_DEVICE", (short) 5, "BLOCK", (short) -60, 1.0));
        policies.add(new TemplateTrustPolicy("PROD_SCORE_JAILBROKEN_IOS_DEVICE", "SYSTEM_RULE", "PROD_JAILBROKEN_IOS_DEVICE", (short) 5, "BLOCK", (short) -60, 1.0));
        policies.add(new TemplateTrustPolicy("PROD_SCORE_USB_DEBUGGING_ENABLED", "SYSTEM_RULE", "PROD_USB_DEBUGGING_ENABLED", (short) 4, "QUARANTINE", (short) -45, 1.0));
        policies.add(new TemplateTrustPolicy("PROD_SCORE_RUNNING_ON_EMULATOR", "SYSTEM_RULE", "PROD_RUNNING_ON_EMULATOR", (short) 4, "QUARANTINE", (short) -45, 1.0));
        for (String osType : NON_ANDROID_VIRTUAL_DEVICE_OS_TYPES) {
            policies.add(new TemplateTrustPolicy(
                    virtualDeviceTrustPolicyCode(osType),
                    "SYSTEM_RULE",
                    virtualDeviceRuleCode(osType),
                    (short) 4,
                    "QUARANTINE",
                    (short) -45,
                    1.0
            ));
        }
        policies.add(new TemplateTrustPolicy("PROD_SCORE_REMOTE_ADMIN_TOOL", "REJECT_APPLICATION", "REMOTE_ADMIN_TOOL", (short) 4, "BLOCK", (short) -45, 1.0));
        policies.add(new TemplateTrustPolicy("PROD_SCORE_OS_EOL", "POSTURE_SIGNAL", "OS_EOL", null, null, (short) -25, 1.0));
        policies.add(new TemplateTrustPolicy("PROD_SCORE_OS_EEOL", "POSTURE_SIGNAL", "OS_EEOL", null, null, (short) -40, 1.0));
        policies.add(new TemplateTrustPolicy("PROD_SCORE_OS_NOT_TRACKED", "POSTURE_SIGNAL", "OS_NOT_TRACKED", null, null, (short) -15, 1.0));
        return List.copyOf(policies);
    }

    private static List<TemplateMapping> buildProductionMappings() {
        List<TemplateMapping> mappings = new ArrayList<>();
        mappings.add(TemplateMapping.forSystemRule("PROD_ROOTED_ANDROID_DEVICE", "PROD_FIX_ROOT_ACCESS", "MANUAL", (short) 1));
        mappings.add(TemplateMapping.forSystemRule("PROD_JAILBROKEN_IOS_DEVICE", "PROD_FIX_JAILBREAK_ACCESS", "MANUAL", (short) 1));
        mappings.add(TemplateMapping.forSystemRule("PROD_USB_DEBUGGING_ENABLED", "PROD_FIX_USB_DEBUGGING", "MANUAL", (short) 1));
        mappings.add(TemplateMapping.forSystemRule("PROD_RUNNING_ON_EMULATOR", "PROD_FIX_EMULATOR_USAGE", "MANUAL", (short) 1));
        for (String osType : NON_ANDROID_VIRTUAL_DEVICE_OS_TYPES) {
            mappings.add(TemplateMapping.forSystemRule(virtualDeviceRuleCode(osType), "PROD_FIX_NON_PHYSICAL_DEVICE", "MANUAL", (short) 1));
        }
        mappings.add(TemplateMapping.forRejectApp("ANYDESK_WINDOWS", "PROD_FIX_REMOVE_REMOTE_ADMIN_TOOL", "MANUAL", (short) 1));
        mappings.add(TemplateMapping.forRejectApp("TEAMVIEWER_WINDOWS", "PROD_FIX_REMOVE_REMOTE_ADMIN_TOOL", "MANUAL", (short) 1));
        mappings.add(TemplateMapping.forTrustPolicy("PROD_SCORE_OS_EOL", "PROD_FIX_UPDATE_OS_SUPPORTED", "ADVISORY", (short) 1));
        mappings.add(TemplateMapping.forTrustPolicy("PROD_SCORE_OS_EEOL", "PROD_FIX_REPLACE_UNSUPPORTED_OS", "ADVISORY", (short) 1));
        mappings.add(TemplateMapping.forTrustPolicy("PROD_SCORE_OS_NOT_TRACKED", "PROD_FIX_UPDATE_OS_SUPPORTED", "ADVISORY", (short) 2));
        return List.copyOf(mappings);
    }

    private static String virtualDeviceRuleCode(String osType) {
        return "PROD_" + osType + "_VIRTUAL_DEVICE";
    }

    private static String virtualDeviceTrustPolicyCode(String osType) {
        return "PROD_SCORE_" + osType + "_VIRTUAL_DEVICE";
    }

    private static String virtualDeviceRuleTag(String osType) {
        return "Virtualized " + displayOsName(osType) + " device";
    }

    private static String displayOsName(String osType) {
        return switch (osType) {
            case "MACOS" -> "macOS";
            case "CHROMEOS" -> "ChromeOS";
            case "FREEBSD" -> "FreeBSD";
            case "OPENBSD" -> "OpenBSD";
            case "IOS" -> "iOS";
            default -> osType.substring(0, 1) + osType.substring(1).toLowerCase(Locale.ROOT);
        };
    }

    public Mono<PolicyTemplateApplyReport> applyProductionPack(String actor,
                                                               boolean includeTenantScopes,
                                                               boolean clearPolicyAudit) {
        String normalizedActor = normalizeActor(actor);
        return blockingDb.mono(() -> transactionTemplate.execute(status -> applyProductionPackInternal(
                normalizedActor,
                includeTenantScopes,
                clearPolicyAudit
        )));
    }

    private PolicyTemplateApplyReport applyProductionPackInternal(String actor,
                                                                  boolean includeTenantScopes,
                                                                  boolean clearPolicyAudit) {
        OffsetDateTime now = OffsetDateTime.now();
        ApplyCounts counts = new ApplyCounts();

        List<SystemInformationRule> allRules = toList(systemRuleRepository.findAll());
        List<SystemInformationRuleCondition> allConditions = toList(conditionRepository.findAll());
        List<RejectApplication> allRejectApps = toList(rejectApplicationRepository.findAll());
        List<TrustScorePolicy> allTrustPolicies = toList(trustScorePolicyRepository.findAll());
        List<TrustScoreDecisionPolicy> allDecisionPolicies = toList(trustScoreDecisionPolicyRepository.findAll());
        List<RemediationRule> allRemediations = toList(remediationRuleRepository.findAll());
        List<RuleRemediationMapping> allMappings = toList(ruleRemediationMappingRepository.findAll());

        if (clearPolicyAudit) {
            counts.clearedPolicyAuditRows = toList(policyChangeAuditRepository.findAll()).size();
            policyChangeAuditRepository.deleteAll();
        }

        Map<Long, List<SystemInformationRuleCondition>> conditionsByRuleId = new HashMap<>();
        for (SystemInformationRuleCondition condition : allConditions) {
            if (condition.getSystemInformationRuleId() == null) {
                continue;
            }
            conditionsByRuleId.computeIfAbsent(condition.getSystemInformationRuleId(), ignored -> new ArrayList<>()).add(condition);
        }

        Map<String, RemediationRule> remediationsByCode = indexRemediationsByCode(allRemediations);
        Map<String, SystemInformationRule> rulesByCode = indexRulesByCode(allRules);
        Map<String, RejectApplication> rejectAppsByKey = indexRejectAppsByKey(allRejectApps);
        Map<String, TrustScorePolicy> trustPoliciesByCode = indexTrustPoliciesByCode(allTrustPolicies);
        Map<String, TrustScoreDecisionPolicy> decisionPoliciesByName = indexDecisionPoliciesByName(allDecisionPolicies);
        Map<String, RuleRemediationMapping> mappingsByKey = indexMappingsByKey(allMappings);

        retireMappings(allMappings, includeTenantScopes, now, actor, counts);
        retireConditions(allRules, conditionsByRuleId, includeTenantScopes, now, actor, counts);
        retireRules(allRules, includeTenantScopes, now, actor, counts);
        retireRejectApps(allRejectApps, includeTenantScopes, now, actor, counts);
        retireTrustPolicies(allTrustPolicies, includeTenantScopes, now, actor, counts);
        retireDecisionPolicies(allDecisionPolicies, includeTenantScopes, now, actor, counts);
        retireRemediations(allRemediations, includeTenantScopes, now, actor, counts);

        Map<String, RemediationRule> appliedRemediations = new HashMap<>();
        for (TemplateRemediation template : PRODUCTION_REMEDIATIONS) {
            RemediationRule remediationRule = upsertRemediation(template, remediationsByCode, now, actor);
            appliedRemediations.put(template.code(), remediationRule);
            counts.appliedRemediationRules++;
        }

        Map<String, SystemInformationRule> appliedRules = new HashMap<>();
        for (TemplateSystemRule template : PRODUCTION_SYSTEM_RULES) {
            SystemInformationRule rule = upsertSystemRule(template, rulesByCode, now, actor);
            appliedRules.put(template.ruleCode(), rule);
            counts.appliedSystemRules++;

            List<SystemInformationRuleCondition> existingRows = conditionsByRuleId.computeIfAbsent(rule.getId(), ignored -> new ArrayList<>());
            SystemInformationRuleCondition condition = upsertRuleCondition(rule, template, existingRows, now, actor);
            if (!existingRows.contains(condition)) {
                existingRows.add(condition);
            }
            counts.appliedSystemRuleConditions++;
        }

        Map<String, RejectApplication> appliedRejectApps = new HashMap<>();
        for (TemplateRejectApp template : PRODUCTION_REJECT_APPS) {
            RejectApplication rejectApplication = upsertRejectApp(template, rejectAppsByKey, now, actor);
            appliedRejectApps.put(template.key(), rejectApplication);
            counts.appliedRejectApps++;
        }

        Map<String, TrustScorePolicy> appliedTrustPolicies = new HashMap<>();
        for (TemplateTrustPolicy template : PRODUCTION_TRUST_POLICIES) {
            TrustScorePolicy trustScorePolicy = upsertTrustPolicy(template, trustPoliciesByCode, now, actor);
            appliedTrustPolicies.put(template.policyCode(), trustScorePolicy);
            counts.appliedTrustScorePolicies++;
        }

        for (TemplateDecisionPolicy template : PRODUCTION_DECISION_POLICIES) {
            upsertDecisionPolicy(template, decisionPoliciesByName, now, actor);
            counts.appliedTrustDecisionPolicies++;
        }

        for (TemplateMapping template : PRODUCTION_MAPPINGS) {
            RuleRemediationMapping mapping = upsertMapping(
                    template,
                    mappingsByKey,
                    appliedRules,
                    appliedRejectApps,
                    appliedTrustPolicies,
                    appliedRemediations,
                    now,
                    actor
            );
            mappingsByKey.put(mappingKey(mapping), mapping);
            counts.appliedRuleRemediationMappings++;
        }

        return new PolicyTemplateApplyReport(
                PACK_NAME,
                actor,
                includeTenantScopes,
                clearPolicyAudit,
                counts.clearedPolicyAuditRows,
                counts.retiredSystemRules,
                counts.retiredSystemRuleConditions,
                counts.retiredRejectApps,
                counts.retiredTrustScorePolicies,
                counts.retiredTrustDecisionPolicies,
                counts.retiredRemediationRules,
                counts.retiredRuleRemediationMappings,
                counts.appliedSystemRules,
                counts.appliedSystemRuleConditions,
                counts.appliedRejectApps,
                counts.appliedTrustScorePolicies,
                counts.appliedTrustDecisionPolicies,
                counts.appliedRemediationRules,
                counts.appliedRuleRemediationMappings,
                now
        );
    }

    private void retireMappings(List<RuleRemediationMapping> mappings,
                                boolean includeTenantScopes,
                                OffsetDateTime now,
                                String actor,
                                ApplyCounts counts) {
        for (RuleRemediationMapping mapping : mappings) {
            if (!shouldRetireScope(mapping.getTenantId(), includeTenantScopes) || mapping.isDeleted()) {
                continue;
            }
            String beforeJson = toAuditJson(mapping);
            mapping.setDeleted(true);
            mapping.setStatus("INACTIVE");
            mapping.setEffectiveTo(resolveEffectiveTo(mapping.getEffectiveFrom(), mapping.getEffectiveTo(), now));
            mapping.setModifiedAt(now);
            mapping.setModifiedBy(actor);
            RuleRemediationMapping saved = ruleRemediationMappingRepository.save(mapping);
            recordAudit("RULE_REMEDIATION_MAPPING", "DELETE", actor, saved.getTenantId(), saved.getId(), beforeJson, null);
            counts.retiredRuleRemediationMappings++;
        }
    }

    private void retireConditions(List<SystemInformationRule> rules,
                                  Map<Long, List<SystemInformationRuleCondition>> conditionsByRuleId,
                                  boolean includeTenantScopes,
                                  OffsetDateTime now,
                                  String actor,
                                  ApplyCounts counts) {
        for (SystemInformationRule rule : rules) {
            if (!shouldRetireScope(rule.getTenantId(), includeTenantScopes) || rule.getId() == null) {
                continue;
            }
            for (SystemInformationRuleCondition condition : conditionsByRuleId.getOrDefault(rule.getId(), List.of())) {
                if (condition.isDeleted()) {
                    continue;
                }
                String beforeJson = toAuditJson(condition);
                condition.setDeleted(true);
                condition.setStatus("INACTIVE");
                condition.setModifiedAt(now);
                condition.setModifiedBy(actor);
                SystemInformationRuleCondition saved = conditionRepository.save(condition);
                recordAudit("SYSTEM_RULE_CONDITION", "DELETE", actor, rule.getTenantId(), saved.getId(), beforeJson, null);
                counts.retiredSystemRuleConditions++;
            }
        }
    }

    private void retireRules(List<SystemInformationRule> rules,
                             boolean includeTenantScopes,
                             OffsetDateTime now,
                             String actor,
                             ApplyCounts counts) {
        for (SystemInformationRule rule : rules) {
            if (!shouldRetireScope(rule.getTenantId(), includeTenantScopes) || rule.isDeleted()) {
                continue;
            }
            String beforeJson = toAuditJson(rule);
            rule.setDeleted(true);
            rule.setStatus("INACTIVE");
            rule.setEffectiveTo(resolveEffectiveTo(rule.getEffectiveFrom(), rule.getEffectiveTo(), now));
            rule.setModifiedAt(now);
            rule.setModifiedBy(actor);
            SystemInformationRule saved = systemRuleRepository.save(rule);
            recordAudit("SYSTEM_RULE", "DELETE", actor, saved.getTenantId(), saved.getId(), beforeJson, null);
            counts.retiredSystemRules++;
        }
    }

    private void retireRejectApps(List<RejectApplication> apps,
                                  boolean includeTenantScopes,
                                  OffsetDateTime now,
                                  String actor,
                                  ApplyCounts counts) {
        for (RejectApplication app : apps) {
            if (!shouldRetireScope(app.getTenantId(), includeTenantScopes) || app.isDeleted()) {
                continue;
            }
            String beforeJson = toAuditJson(app);
            app.setDeleted(true);
            app.setStatus("INACTIVE");
            app.setEffectiveTo(resolveEffectiveTo(app.getEffectiveFrom(), app.getEffectiveTo(), now));
            app.setModifiedAt(now);
            app.setModifiedBy(actor);
            RejectApplication saved = rejectApplicationRepository.save(app);
            recordAudit("REJECT_APPLICATION", "DELETE", actor, saved.getTenantId(), saved.getId(), beforeJson, null);
            counts.retiredRejectApps++;
        }
    }

    private void retireTrustPolicies(List<TrustScorePolicy> policies,
                                     boolean includeTenantScopes,
                                     OffsetDateTime now,
                                     String actor,
                                     ApplyCounts counts) {
        for (TrustScorePolicy policy : policies) {
            if (!shouldRetireScope(policy.getTenantId(), includeTenantScopes) || policy.isDeleted()) {
                continue;
            }
            String beforeJson = toAuditJson(policy);
            policy.setDeleted(true);
            policy.setStatus("INACTIVE");
            policy.setEffectiveTo(resolveEffectiveTo(policy.getEffectiveFrom(), policy.getEffectiveTo(), now));
            policy.setModifiedAt(now);
            policy.setModifiedBy(actor);
            TrustScorePolicy saved = trustScorePolicyRepository.save(policy);
            recordAudit("TRUST_SCORE_POLICY", "DELETE", actor, saved.getTenantId(), saved.getId(), beforeJson, null);
            counts.retiredTrustScorePolicies++;
        }
    }

    private void retireDecisionPolicies(List<TrustScoreDecisionPolicy> policies,
                                        boolean includeTenantScopes,
                                        OffsetDateTime now,
                                        String actor,
                                        ApplyCounts counts) {
        for (TrustScoreDecisionPolicy policy : policies) {
            if (!shouldRetireScope(policy.getTenantId(), includeTenantScopes) || policy.isDeleted()) {
                continue;
            }
            String beforeJson = toAuditJson(policy);
            policy.setDeleted(true);
            policy.setStatus("INACTIVE");
            policy.setEffectiveTo(resolveEffectiveTo(policy.getEffectiveFrom(), policy.getEffectiveTo(), now));
            policy.setModifiedAt(now);
            policy.setModifiedBy(actor);
            TrustScoreDecisionPolicy saved = trustScoreDecisionPolicyRepository.save(policy);
            recordAudit("TRUST_DECISION_POLICY", "DELETE", actor, saved.getTenantId(), saved.getId(), beforeJson, null);
            counts.retiredTrustDecisionPolicies++;
        }
    }

    private void retireRemediations(List<RemediationRule> remediations,
                                    boolean includeTenantScopes,
                                    OffsetDateTime now,
                                    String actor,
                                    ApplyCounts counts) {
        for (RemediationRule remediation : remediations) {
            if (!shouldRetireScope(remediation.getTenantId(), includeTenantScopes) || remediation.isDeleted()) {
                continue;
            }
            String beforeJson = toAuditJson(remediation);
            remediation.setDeleted(true);
            remediation.setStatus("INACTIVE");
            remediation.setEffectiveTo(resolveEffectiveTo(remediation.getEffectiveFrom(), remediation.getEffectiveTo(), now));
            remediation.setModifiedAt(now);
            remediation.setModifiedBy(actor);
            RemediationRule saved = remediationRuleRepository.save(remediation);
            recordAudit("REMEDIATION_RULE", "DELETE", actor, saved.getTenantId(), saved.getId(), beforeJson, null);
            counts.retiredRemediationRules++;
        }
    }

    private RemediationRule upsertRemediation(TemplateRemediation template,
                                              Map<String, RemediationRule> remediationsByCode,
                                              OffsetDateTime now,
                                              String actor) {
        String key = normalizeUpper(template.code());
        RemediationRule existing = remediationsByCode.get(key);
        String beforeJson = toAuditJson(existing);
        RemediationRule rule = existing == null ? new RemediationRule() : existing;
        if (existing == null) {
            rule.setCreatedAt(now);
            rule.setCreatedBy(actor);
        }
        rule.setRemediationCode(template.code());
        rule.setTitle(template.title());
        rule.setDescription(template.description());
        rule.setRemediationType(template.remediationType());
        rule.setOsType(template.osType());
        rule.setDeviceType(template.deviceType());
        rule.setInstructionJson(template.instructionJson());
        rule.setPriority(template.priority());
        rule.setTenantId(null);
        rule.setStatus("ACTIVE");
        rule.setEffectiveFrom(now);
        rule.setEffectiveTo(null);
        rule.setDeleted(false);
        rule.setModifiedAt(now);
        rule.setModifiedBy(actor);
        RemediationRule saved = remediationRuleRepository.save(rule);
        remediationsByCode.put(key, saved);
        recordAudit("REMEDIATION_RULE", existing == null ? "CREATE" : "UPDATE", actor, null, saved.getId(), beforeJson, toAuditJson(saved));
        return saved;
    }

    private SystemInformationRule upsertSystemRule(TemplateSystemRule template,
                                                   Map<String, SystemInformationRule> rulesByCode,
                                                   OffsetDateTime now,
                                                   String actor) {
        String key = normalizeUpper(template.ruleCode());
        SystemInformationRule existing = rulesByCode.get(key);
        String beforeJson = toAuditJson(existing);
        SystemInformationRule rule = existing == null ? new SystemInformationRule() : existing;
        if (existing == null) {
            rule.setCreatedAt(now);
            rule.setCreatedBy(actor);
            rule.setVersion(1);
        } else {
            rule.setVersion(Math.max(1, safeInt(existing.getVersion(), 1)) + 1);
        }
        rule.setRuleCode(template.ruleCode());
        rule.setRuleTag(template.ruleTag());
        rule.setDescription(template.description());
        rule.setOsType(template.osType());
        rule.setOsName(null);
        rule.setDeviceType(template.deviceType());
        rule.setSeverity(template.severity());
        rule.setPriority(template.priority());
        rule.setMatchMode("ALL");
        rule.setComplianceAction(template.complianceAction());
        rule.setRiskScoreDelta(template.riskScoreDelta());
        rule.setTenantId(null);
        rule.setStatus("ACTIVE");
        rule.setEffectiveFrom(now);
        rule.setEffectiveTo(null);
        rule.setDeleted(false);
        rule.setModifiedAt(now);
        rule.setModifiedBy(actor);
        SystemInformationRule saved = systemRuleRepository.save(rule);
        rulesByCode.put(key, saved);
        recordAudit("SYSTEM_RULE", existing == null ? "CREATE" : "UPDATE", actor, null, saved.getId(), beforeJson, toAuditJson(saved));
        return saved;
    }

    private SystemInformationRuleCondition upsertRuleCondition(SystemInformationRule rule,
                                                               TemplateSystemRule template,
                                                               List<SystemInformationRuleCondition> existingRows,
                                                               OffsetDateTime now,
                                                               String actor) {
        SystemInformationRuleCondition existing = existingRows.stream().findFirst().orElse(null);
        String beforeJson = toAuditJson(existing);
        SystemInformationRuleCondition condition = existing == null ? new SystemInformationRuleCondition() : existing;
        if (existing == null) {
            condition.setCreatedAt(now);
            condition.setCreatedBy(actor);
        }
        condition.setSystemInformationRuleId(rule.getId());
        condition.setConditionGroup((short) 1);
        condition.setFieldName(template.fieldName());
        condition.setOperator(template.operator());
        condition.setValueText(template.valueText());
        condition.setValueNumeric(template.valueNumeric());
        condition.setValueBoolean(template.valueBoolean());
        condition.setValueJson(null);
        condition.setWeight((short) 1);
        condition.setStatus("ACTIVE");
        condition.setDeleted(false);
        condition.setModifiedAt(now);
        condition.setModifiedBy(actor);
        SystemInformationRuleCondition saved = conditionRepository.save(condition);
        recordAudit("SYSTEM_RULE_CONDITION", existing == null ? "CREATE" : "UPDATE", actor, rule.getTenantId(), saved.getId(), beforeJson, toAuditJson(saved));
        return saved;
    }

    private RejectApplication upsertRejectApp(TemplateRejectApp template,
                                              Map<String, RejectApplication> rejectAppsByKey,
                                              OffsetDateTime now,
                                              String actor) {
        String key = rejectAppKey(null, template.appOsType(), template.packageId(), template.appName(), template.policyTag());
        RejectApplication existing = rejectAppsByKey.get(key);
        String beforeJson = toAuditJson(existing);
        RejectApplication app = existing == null ? new RejectApplication() : existing;
        if (existing == null) {
            app.setCreatedAt(now);
            app.setCreatedBy(actor);
        }
        app.setPolicyTag(template.policyTag());
        app.setSeverity(template.severity());
        app.setAppName(template.appName());
        app.setPublisher(template.publisher());
        app.setPackageId(template.packageId());
        app.setAppOsType(template.appOsType());
        app.setMinAllowedVersion(defaultIfBlank(template.minAllowedVersion(), "999999.0.0"));
        app.setTenantId(null);
        app.setStatus("ACTIVE");
        app.setEffectiveFrom(now);
        app.setEffectiveTo(null);
        app.setDeleted(false);
        app.setModifiedAt(now);
        app.setModifiedBy(actor);
        RejectApplication saved = rejectApplicationRepository.save(app);
        rejectAppsByKey.put(key, saved);
        recordAudit("REJECT_APPLICATION", existing == null ? "CREATE" : "UPDATE", actor, null, saved.getId(), beforeJson, toAuditJson(saved));
        return saved;
    }

    private TrustScorePolicy upsertTrustPolicy(TemplateTrustPolicy template,
                                               Map<String, TrustScorePolicy> trustPoliciesByCode,
                                               OffsetDateTime now,
                                               String actor) {
        String key = normalizeUpper(template.policyCode());
        TrustScorePolicy existing = trustPoliciesByCode.get(key);
        String beforeJson = toAuditJson(existing);
        TrustScorePolicy policy = existing == null ? new TrustScorePolicy() : existing;
        if (existing == null) {
            policy.setCreatedAt(now);
            policy.setCreatedBy(actor);
        }
        policy.setPolicyCode(template.policyCode());
        policy.setSourceType(template.sourceType());
        policy.setSignalKey(template.signalKey());
        policy.setSeverity(template.severity());
        policy.setComplianceAction(template.complianceAction());
        policy.setScoreDelta(template.scoreDelta());
        policy.setWeight(template.weight());
        policy.setTenantId(null);
        policy.setStatus("ACTIVE");
        policy.setEffectiveFrom(now);
        policy.setEffectiveTo(null);
        policy.setDeleted(false);
        policy.setModifiedAt(now);
        policy.setModifiedBy(actor);
        TrustScorePolicy saved = trustScorePolicyRepository.save(policy);
        trustPoliciesByCode.put(key, saved);
        recordAudit("TRUST_SCORE_POLICY", existing == null ? "CREATE" : "UPDATE", actor, null, saved.getId(), beforeJson, toAuditJson(saved));
        return saved;
    }

    private TrustScoreDecisionPolicy upsertDecisionPolicy(TemplateDecisionPolicy template,
                                                          Map<String, TrustScoreDecisionPolicy> decisionPoliciesByName,
                                                          OffsetDateTime now,
                                                          String actor) {
        String key = normalizeLookupKey(template.policyName());
        TrustScoreDecisionPolicy existing = decisionPoliciesByName.get(key);
        String beforeJson = toAuditJson(existing);
        TrustScoreDecisionPolicy policy = existing == null ? new TrustScoreDecisionPolicy() : existing;
        if (existing == null) {
            policy.setCreatedAt(now);
            policy.setCreatedBy(actor);
        }
        policy.setPolicyName(template.policyName());
        policy.setScoreMin(template.scoreMin());
        policy.setScoreMax(template.scoreMax());
        policy.setDecisionAction(template.decisionAction());
        policy.setRemediationRequired(template.remediationRequired());
        policy.setResponseMessage(template.responseMessage());
        policy.setTenantId(null);
        policy.setStatus("ACTIVE");
        policy.setEffectiveFrom(now);
        policy.setEffectiveTo(null);
        policy.setDeleted(false);
        policy.setModifiedAt(now);
        policy.setModifiedBy(actor);
        TrustScoreDecisionPolicy saved = trustScoreDecisionPolicyRepository.save(policy);
        decisionPoliciesByName.put(key, saved);
        recordAudit("TRUST_DECISION_POLICY", existing == null ? "CREATE" : "UPDATE", actor, null, saved.getId(), beforeJson, toAuditJson(saved));
        return saved;
    }

    private RuleRemediationMapping upsertMapping(TemplateMapping template,
                                                 Map<String, RuleRemediationMapping> mappingsByKey,
                                                 Map<String, SystemInformationRule> rulesByCode,
                                                 Map<String, RejectApplication> rejectAppsByKey,
                                                 Map<String, TrustScorePolicy> trustPoliciesByCode,
                                                 Map<String, RemediationRule> remediationsByCode,
                                                 OffsetDateTime now,
                                                 String actor) {
        SystemInformationRule systemRule = template.systemRuleCode() == null ? null : rulesByCode.get(template.systemRuleCode());
        RejectApplication rejectApplication = template.rejectAppKey() == null ? null : rejectAppsByKey.get(template.rejectAppKey());
        TrustScorePolicy trustScorePolicy = template.trustPolicyCode() == null ? null : trustPoliciesByCode.get(template.trustPolicyCode());
        RemediationRule remediationRule = remediationsByCode.get(template.remediationCode());

        RuleRemediationMapping probe = new RuleRemediationMapping();
        probe.setSourceType(template.sourceType());
        probe.setSystemInformationRuleId(systemRule == null ? null : systemRule.getId());
        probe.setRejectApplicationListId(rejectApplication == null ? null : rejectApplication.getId());
        probe.setTrustScorePolicyId(trustScorePolicy == null ? null : trustScorePolicy.getId());
        probe.setDecisionAction(template.decisionAction());
        probe.setRemediationRuleId(remediationRule == null ? null : remediationRule.getId());
        probe.setTenantId(null);

        String key = mappingKey(probe);
        RuleRemediationMapping existing = mappingsByKey.get(key);
        String beforeJson = toAuditJson(existing);
        RuleRemediationMapping mapping = existing == null ? new RuleRemediationMapping() : existing;
        if (existing == null) {
            mapping.setCreatedAt(now);
            mapping.setCreatedBy(actor);
        }
        mapping.setSourceType(template.sourceType());
        mapping.setSystemInformationRuleId(systemRule == null ? null : systemRule.getId());
        mapping.setRejectApplicationListId(rejectApplication == null ? null : rejectApplication.getId());
        mapping.setTrustScorePolicyId(trustScorePolicy == null ? null : trustScorePolicy.getId());
        mapping.setDecisionAction(template.decisionAction());
        mapping.setRemediationRuleId(remediationRule == null ? null : remediationRule.getId());
        mapping.setEnforceMode(template.enforceMode());
        mapping.setRankOrder(template.rankOrder());
        mapping.setTenantId(null);
        mapping.setStatus("ACTIVE");
        mapping.setEffectiveFrom(now);
        mapping.setEffectiveTo(null);
        mapping.setDeleted(false);
        mapping.setModifiedAt(now);
        mapping.setModifiedBy(actor);
        RuleRemediationMapping saved = ruleRemediationMappingRepository.save(mapping);
        recordAudit("RULE_REMEDIATION_MAPPING", existing == null ? "CREATE" : "UPDATE", actor, null, saved.getId(), beforeJson, toAuditJson(saved));
        return saved;
    }

    private Map<String, RemediationRule> indexRemediationsByCode(List<RemediationRule> rows) {
        Map<String, RemediationRule> out = new HashMap<>();
        for (RemediationRule row : rows) {
            if (normalizeOptionalTenantId(row.getTenantId()) != null) {
                continue;
            }
            String key = normalizeUpper(row.getRemediationCode());
            if (key != null) {
                out.putIfAbsent(key, row);
            }
        }
        return out;
    }

    private Map<String, SystemInformationRule> indexRulesByCode(List<SystemInformationRule> rows) {
        Map<String, SystemInformationRule> out = new HashMap<>();
        for (SystemInformationRule row : rows) {
            if (normalizeOptionalTenantId(row.getTenantId()) != null) {
                continue;
            }
            String key = normalizeUpper(row.getRuleCode());
            if (key != null) {
                out.putIfAbsent(key, row);
            }
        }
        return out;
    }

    private Map<String, RejectApplication> indexRejectAppsByKey(List<RejectApplication> rows) {
        Map<String, RejectApplication> out = new HashMap<>();
        for (RejectApplication row : rows) {
            if (normalizeOptionalTenantId(row.getTenantId()) != null) {
                continue;
            }
            out.putIfAbsent(rejectAppKey(row.getTenantId(), row.getAppOsType(), row.getPackageId(), row.getAppName(), row.getPolicyTag()), row);
        }
        return out;
    }

    private Map<String, TrustScorePolicy> indexTrustPoliciesByCode(List<TrustScorePolicy> rows) {
        Map<String, TrustScorePolicy> out = new HashMap<>();
        for (TrustScorePolicy row : rows) {
            if (normalizeOptionalTenantId(row.getTenantId()) != null) {
                continue;
            }
            String key = normalizeUpper(row.getPolicyCode());
            if (key != null) {
                out.putIfAbsent(key, row);
            }
        }
        return out;
    }

    private Map<String, TrustScoreDecisionPolicy> indexDecisionPoliciesByName(List<TrustScoreDecisionPolicy> rows) {
        Map<String, TrustScoreDecisionPolicy> out = new HashMap<>();
        for (TrustScoreDecisionPolicy row : rows) {
            if (normalizeOptionalTenantId(row.getTenantId()) != null) {
                continue;
            }
            String key = normalizeLookupKey(row.getPolicyName());
            if (key != null) {
                out.putIfAbsent(key, row);
            }
        }
        return out;
    }

    private Map<String, RuleRemediationMapping> indexMappingsByKey(List<RuleRemediationMapping> rows) {
        Map<String, RuleRemediationMapping> out = new HashMap<>();
        for (RuleRemediationMapping row : rows) {
            if (normalizeOptionalTenantId(row.getTenantId()) != null) {
                continue;
            }
            out.putIfAbsent(mappingKey(row), row);
        }
        return out;
    }

    private boolean shouldRetireScope(String tenantId, boolean includeTenantScopes) {
        return includeTenantScopes || normalizeOptionalTenantId(tenantId) == null;
    }

    private OffsetDateTime resolveEffectiveTo(OffsetDateTime effectiveFrom, OffsetDateTime effectiveTo, OffsetDateTime now) {
        if (effectiveTo != null && !effectiveTo.isAfter(now)) {
            return effectiveTo;
        }
        if (effectiveFrom != null && !now.isAfter(effectiveFrom)) {
            return effectiveFrom.plusSeconds(1);
        }
        return now;
    }

    private String rejectAppKey(String tenantId,
                                String appOsType,
                                String packageId,
                                String appName,
                                String policyTag) {
        return defaultIfBlank(normalizeOptionalTenantId(tenantId), "global")
                + "|"
                + defaultIfBlank(normalizeUpper(appOsType), "UNKNOWN")
                + "|"
                + defaultIfBlank(normalizeLookupKey(packageId), "")
                + "|"
                + defaultIfBlank(normalizeLookupKey(appName), "")
                + "|"
                + defaultIfBlank(normalizeUpper(policyTag), "");
    }

    private String mappingKey(RuleRemediationMapping mapping) {
        return defaultIfBlank(normalizeOptionalTenantId(mapping.getTenantId()), "global")
                + "|"
                + defaultIfBlank(normalizeUpper(mapping.getSourceType()), "UNKNOWN")
                + "|"
                + safeLong(mapping.getSystemInformationRuleId())
                + "|"
                + safeLong(mapping.getRejectApplicationListId())
                + "|"
                + safeLong(mapping.getTrustScorePolicyId())
                + "|"
                + defaultIfBlank(normalizeUpper(mapping.getDecisionAction()), "")
                + "|"
                + safeLong(mapping.getRemediationRuleId());
    }

    private long safeLong(Long value) {
        return value == null ? 0L : value;
    }

    private int safeInt(Integer value, int fallback) {
        return value == null ? fallback : value;
    }

    private <T> List<T> toList(Iterable<T> rows) {
        if (rows == null) {
            return List.of();
        }
        List<T> list = new ArrayList<>();
        rows.forEach(list::add);
        return list;
    }

    private String normalizeUpper(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeLookupKey(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeOptionalTenantId(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            return null;
        }
        return tenantId.trim().toLowerCase(Locale.ROOT);
    }

    private String defaultIfBlank(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }

    private String normalizeActor(String actor) {
        if (actor == null || actor.isBlank()) {
            return "system";
        }
        return actor.trim();
    }

    private void recordAudit(String policyType,
                             String operation,
                             String actor,
                             String tenantId,
                             Long policyId,
                             String beforeJson,
                             String afterJson) {
        PolicyChangeAudit audit = new PolicyChangeAudit();
        audit.setPolicyType(policyType);
        audit.setPolicyId(policyId);
        audit.setOperation(operation);
        audit.setTenantId(normalizeOptionalTenantId(tenantId));
        audit.setActor(normalizeActor(actor));
        audit.setApprovalTicket(null);
        audit.setBeforeStateJson(beforeJson);
        audit.setAfterStateJson(afterJson);
        audit.setCreatedAt(OffsetDateTime.now());

        PolicyAuditMessage message = toAuditMessage(audit);
        try {
            policyAuditPublisher.publish(message);
            incrementAuditMetric(policyType, operation, tenantId, "success");
            recordGenericAuditEvent(audit, "SUCCESS");
            return;
        } catch (RuntimeException _) {
            // Best-effort fallback for queue-free or offline environments.
        }

        try {
            policyChangeAuditRepository.save(audit);
            incrementAuditMetric(policyType, operation, tenantId, "success");
            recordGenericAuditEvent(audit, "SUCCESS");
        } catch (RuntimeException ex) {
            incrementAuditMetric(policyType, operation, tenantId, "failure");
            recordGenericAuditEvent(audit, "FAILURE");
            throw ex;
        }
    }

    private void recordGenericAuditEvent(PolicyChangeAudit audit, String status) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("policyType", audit.getPolicyType());
        metadata.put("policyId", audit.getPolicyId());
        metadata.put("operation", audit.getOperation());
        metadata.put("approvalTicket", audit.getApprovalTicket());
        metadata.put("beforeStateJson", audit.getBeforeStateJson());
        metadata.put("afterStateJson", audit.getAfterStateJson());

        auditEventService.recordBestEffort(
                "POLICY",
                "POLICY_TEMPLATE_APPLY",
                audit.getOperation(),
                audit.getTenantId(),
                audit.getActor(),
                audit.getPolicyType(),
                audit.getPolicyId() == null ? null : String.valueOf(audit.getPolicyId()),
                status,
                metadata
        );
    }

    private void incrementAuditMetric(String policyType, String operation, String tenantId, String outcome) {
        meterRegistry.counter(
                "mdm.policy.audit.events",
                "policy_type", normalizeMetricTag(policyType),
                "operation", normalizeMetricTag(operation),
                "scope", normalizeOptionalTenantId(tenantId) == null ? "global" : "tenant",
                "outcome", normalizeMetricTag(outcome)
        ).increment();
    }

    private PolicyAuditMessage toAuditMessage(PolicyChangeAudit audit) {
        PolicyAuditMessage message = new PolicyAuditMessage();
        message.setSchemaVersion(PolicyAuditMessage.CURRENT_SCHEMA_VERSION);
        message.setEventId(UUID.randomUUID().toString());
        message.setPolicyType(audit.getPolicyType());
        message.setPolicyId(audit.getPolicyId());
        message.setOperation(audit.getOperation());
        message.setTenantId(audit.getTenantId());
        message.setActor(audit.getActor());
        message.setApprovalTicket(audit.getApprovalTicket());
        message.setBeforeStateJson(audit.getBeforeStateJson());
        message.setAfterStateJson(audit.getAfterStateJson());
        message.setCreatedAt(audit.getCreatedAt());
        return message;
    }

    private String normalizeMetricTag(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_\\-]+", "_");
    }

    private String toAuditJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return "{\"serialization_error\":true}";
        }
    }

}
