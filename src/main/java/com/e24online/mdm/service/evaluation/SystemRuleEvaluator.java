package com.e24online.mdm.service;

import com.e24online.mdm.domain.SystemInformationRule;
import com.e24online.mdm.domain.SystemInformationRuleCondition;
import com.e24online.mdm.domain.TrustScorePolicy;
import com.e24online.mdm.records.posture.evaluation.AppliedPolicy;
import com.e24online.mdm.records.posture.evaluation.LifecycleResolution;
import com.e24online.mdm.records.posture.evaluation.MatchDraft;
import com.e24online.mdm.records.posture.evaluation.ParsedPosture;
import com.e24online.mdm.records.posture.evaluation.ScoreSignal;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
class SystemRuleEvaluator {

    private final EvaluationSupport support;
    private final TrustPolicyResolver trustPolicyResolver;

    SystemRuleEvaluator(EvaluationSupport support,
                        TrustPolicyResolver trustPolicyResolver) {
        this.support = support;
        this.trustPolicyResolver = trustPolicyResolver;
    }

    SystemRuleEvaluation evaluate(List<SystemInformationRule> activeRules,
                                  Map<Long, List<SystemInformationRuleCondition>> conditionsByRule,
                                  List<TrustScorePolicy> activePolicies,
                                  ParsedPosture parsed,
                                  LifecycleResolution lifecycle) {
        List<MatchDraft> matches = new ArrayList<>();
        List<ScoreSignal> signals = new ArrayList<>();
        int matchedRuleCount = 0;
        for (SystemInformationRule rule : activeRules) {
            List<SystemInformationRuleCondition> conditions = conditionsByRule.getOrDefault(rule.getId(), List.of());
            if (!matchesSystemRule(rule, conditions, parsed)) {
                continue;
            }
            AppliedPolicy applied = trustPolicyResolver.findAppliedPolicy(
                    activePolicies,
                    "SYSTEM_RULE",
                    trustPolicyResolver.signalCandidates(rule.getRuleCode(), rule.getRuleTag()),
                    rule.getSeverity(),
                    rule.getComplianceAction(),
                    parsed.tenantId()
            );
            short delta = applied != null ? support.weightedDelta(applied.policy()) : support.safeShort(rule.getRiskScoreDelta(), (short) 0);
            matches.add(new MatchDraft(
                    "SYSTEM_RULE",
                    rule.getId(),
                    null,
                    null,
                    null,
                    lifecycle.state(),
                    null,
                    support.safeShort(rule.getSeverity(), null),
                    support.normalizeDecision(rule.getComplianceAction()),
                    delta,
                    support.toJson(Map.of("rule_code", support.safeText(rule.getRuleCode()), "rule_tag", support.safeText(rule.getRuleTag())))
            ));
            signals.add(new ScoreSignal(
                    "SYSTEM_RULE",
                    rule.getId(),
                    applied == null ? null : applied.policy().getId(),
                    rule.getId(),
                    null,
                    lifecycle.masterId(),
                    lifecycle.state(),
                    delta,
                    "Matched system rule: " + support.safeText(rule.getRuleCode())
            ));
            matchedRuleCount++;
        }
        return new SystemRuleEvaluation(matches, signals, matchedRuleCount);
    }

    private boolean matchesSystemRule(SystemInformationRule rule,
                                      List<SystemInformationRuleCondition> conditions,
                                      ParsedPosture parsed) {
        if (conditions.isEmpty()) {
            return true;
        }
        Map<Short, List<SystemInformationRuleCondition>> grouped = new HashMap<>();
        for (SystemInformationRuleCondition c : conditions) {
            short group = c.getConditionGroup() == null ? 1 : c.getConditionGroup();
            grouped.computeIfAbsent(group, _ -> new ArrayList<>()).add(c);
        }
        List<Boolean> groupResults = new ArrayList<>();
        for (List<SystemInformationRuleCondition> group : grouped.values()) {
            boolean all = true;
            for (SystemInformationRuleCondition c : group) {
                if (!evaluateCondition(c, parsed)) {
                    all = false;
                    break;
                }
            }
            groupResults.add(all);
        }
        String mode = support.normalizeUpper(rule.getMatchMode());
        return "ANY".equals(mode)
                ? groupResults.stream().anyMatch(Boolean::booleanValue)
                : groupResults.stream().allMatch(Boolean::booleanValue);
    }

    private boolean evaluateCondition(SystemInformationRuleCondition condition, ParsedPosture parsed) {
        String field = support.trimToNull(condition.getFieldName());
        String operator = support.normalizeUpper(condition.getOperator());
        if (field == null || operator == null) {
            return false;
        }
        Object actual = extractFieldValue(field, parsed);
        Object expected = expectedValue(condition);
        return switch (operator) {
            case "EXISTS" -> actual != null;
            case "NOT_EXISTS" -> actual == null;
            case "EQ" -> compareValues(actual, expected) == 0;
            case "NEQ" -> compareValues(actual, expected) != 0;
            case "GT" -> compareValues(actual, expected) > 0;
            case "GTE" -> compareValues(actual, expected) >= 0;
            case "LT" -> compareValues(actual, expected) < 0;
            case "LTE" -> compareValues(actual, expected) <= 0;
            case "IN" -> inOperator(actual, expected, true);
            case "NOT_IN" -> inOperator(actual, expected, false);
            case "REGEX" -> regexMatch(actual, expected);
            default -> false;
        };
    }

    private Object expectedValue(SystemInformationRuleCondition c) {
        if (c.getValueNumeric() != null) {
            return c.getValueNumeric();
        }
        if (c.getValueBoolean() != null) {
            return c.getValueBoolean();
        }
        if (c.getValueJson() != null && !c.getValueJson().isBlank()) {
            try {
                JsonNode json = support.objectMapper().readTree(c.getValueJson());
                if (json.isArray()) {
                    List<String> values = new ArrayList<>();
                    json.forEach(x -> values.add(support.stringifyNode(x)));
                    return values;
                }
                return support.stringifyNode(json);
            } catch (Exception ex) {
                return c.getValueText();
            }
        }
        return c.getValueText();
    }

    private Object extractFieldValue(String field, ParsedPosture parsed) {
        String key = field.toLowerCase(Locale.ROOT);
        Map<String, Object> fixed = new HashMap<>();
        fixed.put("tenant_id", parsed.tenantId());
        fixed.put("device_external_id", parsed.deviceExternalId());
        fixed.put("agent_id", parsed.agentId());
        fixed.put("device_type", parsed.deviceType());
        fixed.put("os_type", parsed.osType());
        fixed.put("os_name", parsed.osName());
        fixed.put("os_version", parsed.osVersion());
        fixed.put("os_cycle", parsed.osCycle());
        fixed.put("time_zone", parsed.timeZone());
        fixed.put("kernel_version", parsed.kernelVersion());
        fixed.put("api_level", parsed.apiLevel());
        fixed.put("os_build_number", parsed.osBuildNumber());
        fixed.put("manufacturer", parsed.manufacturer());
        fixed.put("root_detected", parsed.rootDetected());
        fixed.put("running_on_emulator", parsed.runningOnEmulator());
        fixed.put("usb_debugging_status", parsed.usbDebuggingStatus());
        fixed.put("installed_app_count", parsed.installedApps().size());
        if (fixed.containsKey(key)) {
            return fixed.get(key);
        }
        JsonNode root = parsed.root();
        JsonNode node;
        if (field.contains(".")) {
            node = root;
            for (String part : field.split("\\.")) {
                if (node == null) {
                    break;
                }
                node = node.get(part);
            }
        } else {
            node = root.get(field);
            if (node == null) {
                node = root.get(key);
            }
        }
        if (node == null || node.isNull()) {
            return null;
        }
        return switch (node.getNodeType()) {
            case BOOLEAN -> node.booleanValue();
            case NUMBER -> node.numberValue();
            case STRING -> node.asText();
            default -> node.toString();
        };
    }

    private boolean inOperator(Object actual, Object expected, boolean positive) {
        if (actual == null) {
            return !positive;
        }
        Collection<String> values = support.expectedCollection(expected);
        boolean contains = values.stream().anyMatch(v -> support.equalsIgnoreCase(v, String.valueOf(actual)));
        return positive == contains;
    }

    private boolean regexMatch(Object actual, Object expected) {
        if (actual == null || expected == null) {
            return false;
        }
        try {
            return java.util.regex.Pattern.compile(String.valueOf(expected)).matcher(String.valueOf(actual)).find();
        } catch (Exception ex) {
            return false;
        }
    }

    private int compareValues(Object actual, Object expected) {
        if (actual == null && expected == null) {
            return 0;
        }
        if (actual == null) {
            return -1;
        }
        if (expected == null) {
            return 1;
        }
        Double actualNum = support.toDouble(actual);
        Double expectedNum = support.toDouble(expected);
        if (actualNum != null && expectedNum != null) {
            return Double.compare(actualNum, expectedNum);
        }
        if (actual instanceof Boolean || expected instanceof Boolean) {
            return Boolean.compare(Boolean.parseBoolean(String.valueOf(actual)), Boolean.parseBoolean(String.valueOf(expected)));
        }
        return String.valueOf(actual).compareToIgnoreCase(String.valueOf(expected));
    }

    record SystemRuleEvaluation(List<MatchDraft> matches, List<ScoreSignal> signals, int matchedRuleCount) {
    }
}
