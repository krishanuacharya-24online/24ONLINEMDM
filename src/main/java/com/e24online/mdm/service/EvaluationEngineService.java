package com.e24online.mdm.service;

import com.e24online.mdm.domain.*;
import com.e24online.mdm.records.cache.CacheEntry;
import com.e24online.mdm.records.posture.evaluation.AppliedPolicy;
import com.e24online.mdm.records.posture.evaluation.EvaluationComputation;
import com.e24online.mdm.records.posture.evaluation.LifecycleResolution;
import com.e24online.mdm.records.posture.evaluation.MatchDraft;
import com.e24online.mdm.records.posture.evaluation.ParsedPosture;
import com.e24online.mdm.records.posture.evaluation.ScoreSignal;
import com.e24online.mdm.repository.DeviceTrustScoreEventRepository;
import com.e24online.mdm.repository.RejectApplicationRepository;
import com.e24online.mdm.repository.SystemInformationRuleConditionRepository;
import com.e24online.mdm.repository.SystemInformationRuleRepository;
import com.e24online.mdm.repository.TrustScoreDecisionPolicyRepository;
import com.e24online.mdm.repository.TrustScorePolicyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.scheduler.Scheduler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import static com.e24online.mdm.utils.AgentWorkflowValueUtils.*;

/**
 * Service for evaluating device posture against rules and policies.
 * Handles rule matching, trust score computation, and decision-making.
 */
@Service
public class EvaluationEngineService {

    private static final Logger log = LoggerFactory.getLogger(EvaluationEngineService.class);
    private static final Set<String> DECISION_ACTIONS = Set.of("ALLOW", "QUARANTINE", "BLOCK", "NOTIFY");
    private static final long DEFAULT_REFERENCE_CACHE_SECONDS = 30L;

    private final SystemInformationRuleRepository systemRuleRepository;
    private final SystemInformationRuleConditionRepository conditionRepository;
    private final RejectApplicationRepository rejectApplicationRepository;
    private final TrustScorePolicyRepository trustScorePolicyRepository;
    private final TrustScoreDecisionPolicyRepository trustScoreDecisionPolicyRepository;
    private final DeviceTrustScoreEventRepository scoreEventRepository;
    private final ObjectMapper objectMapper;
    private final Scheduler jdbcScheduler;
    private final ConcurrentMap<String, CacheEntry<List<SystemInformationRule>>> systemRuleCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, CacheEntry<Map<Long, List<SystemInformationRuleCondition>>>> ruleConditionCache =
            new ConcurrentHashMap<>();
    private final ConcurrentMap<String, CacheEntry<List<RejectApplication>>> rejectApplicationCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, CacheEntry<List<TrustScorePolicy>>> trustPolicyCache = new ConcurrentHashMap<>();

    @Value("${mdm.evaluation.reference-cache-seconds:30}")
    private long referenceCacheSeconds = DEFAULT_REFERENCE_CACHE_SECONDS;

    public EvaluationEngineService(SystemInformationRuleRepository systemRuleRepository,
                                   SystemInformationRuleConditionRepository conditionRepository,
                                   RejectApplicationRepository rejectApplicationRepository,
                                   TrustScorePolicyRepository trustScorePolicyRepository,
                                   TrustScoreDecisionPolicyRepository trustScoreDecisionPolicyRepository,
                                   DeviceTrustScoreEventRepository scoreEventRepository,
                                   ObjectMapper objectMapper,
                                   Scheduler jdbcScheduler) {
        this.systemRuleRepository = systemRuleRepository;
        this.conditionRepository = conditionRepository;
        this.rejectApplicationRepository = rejectApplicationRepository;
        this.trustScorePolicyRepository = trustScorePolicyRepository;
        this.trustScoreDecisionPolicyRepository = trustScoreDecisionPolicyRepository;
        this.scoreEventRepository = scoreEventRepository;
        this.objectMapper = objectMapper;
        this.jdbcScheduler = jdbcScheduler;
    }

    /**
     * Compute evaluation results including matches, score signals, and decision.
     */
    public EvaluationComputation computeEvaluation(DeviceTrustProfile profile,
                                                   ParsedPosture parsed,
                                                   List<DeviceInstalledApplication> installedApps,
                                                   LifecycleResolution lifecycle,
                                                   OffsetDateTime now) {
        log.debug("computeEvaluation method");
        List<SystemInformationRule> activeRules = activeSystemRules(parsed, now);
        Map<Long, List<SystemInformationRuleCondition>> conditionsByRule = activeRuleConditions(activeRules);
        List<RejectApplication> activeRejectApps = activeRejectApps(parsed.tenantId(), parsed.osType(), now);
        List<TrustScorePolicy> activePolicies = activeTrustPolicies(parsed.tenantId(), now);

        List<MatchDraft> matches = new ArrayList<>();
        List<ScoreSignal> signals = new ArrayList<>();

        int matchedRuleCount = 0;
        for (SystemInformationRule rule : activeRules) {
            List<SystemInformationRuleCondition> conditions = conditionsByRule.getOrDefault(rule.getId(), List.of());
            if (!matchesSystemRule(rule, conditions, parsed)) {
                continue;
            }

            AppliedPolicy applied = findAppliedPolicy(
                    activePolicies,
                    "SYSTEM_RULE",
                    List.of(rule.getRuleCode(), rule.getRuleTag()),
                    rule.getSeverity(),
                    rule.getComplianceAction(),
                    parsed.tenantId()
            );

            short delta = applied != null ? weightedDelta(applied.policy()) : safeShort(rule.getRiskScoreDelta(), (short) 0);

            matches.add(new MatchDraft(
                    "SYSTEM_RULE",
                    rule.getId(),
                    null,
                    null,
                    null,
                    lifecycle.state(),
                    null,
                    safeShort(rule.getSeverity(), null),
                    normalizeDecision(rule.getComplianceAction()),
                    delta,
                    toJson(Map.of("rule_code", safeText(rule.getRuleCode()), "rule_tag", safeText(rule.getRuleTag())))
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
                    "Matched system rule: " + safeText(rule.getRuleCode())
            ));
            matchedRuleCount++;
        }

        int matchedAppCount = 0;
        for (DeviceInstalledApplication app : installedApps) {
            for (RejectApplication reject : activeRejectApps) {
                if (!matchesRejectApp(app, reject)) {
                    continue;
                }

                AppliedPolicy applied = findAppliedPolicy(
                        activePolicies,
                        "REJECT_APPLICATION",
                        List.of(reject.getPackageId(), reject.getAppName(), reject.getPolicyTag()),
                        reject.getSeverity(),
                        "BLOCK",
                        parsed.tenantId()
                );

                short delta = applied != null ? weightedDelta(applied.policy()) : defaultRejectDelta(safeShort(reject.getSeverity(), (short) 3));

                matches.add(new MatchDraft(
                        "REJECT_APPLICATION",
                        null,
                        reject.getId(),
                        null,
                        null,
                        lifecycle.state(),
                        app.getId(),
                        safeShort(reject.getSeverity(), null),
                        "BLOCK",
                        delta,
                        toJson(Map.of("app_name", safeText(app.getAppName()), "package_id", safeText(app.getPackageId())))
                ));

                signals.add(new ScoreSignal(
                        "REJECT_APPLICATION",
                        reject.getId(),
                        applied == null ? null : applied.policy().getId(),
                        null,
                        reject.getId(),
                        lifecycle.masterId(),
                        lifecycle.state(),
                        delta,
                        "Matched rejected app: " + safeText(app.getAppName())
                ));
                matchedAppCount++;
            }
        }

        if (!"SUPPORTED".equalsIgnoreCase(lifecycle.state())) {
            AppliedPolicy applied = findAppliedPolicy(activePolicies, "POSTURE_SIGNAL", List.of(lifecycle.signalKey()), null, null, parsed.tenantId());
            short delta = applied != null ? weightedDelta(applied.policy()) : defaultLifecycleDelta(lifecycle.state());

            signals.add(new ScoreSignal(
                    "POSTURE_SIGNAL",
                    lifecycle.masterId(),
                    applied == null ? null : applied.policy().getId(),
                    null,
                    null,
                    lifecycle.masterId(),
                    lifecycle.state(),
                    delta,
                    "Lifecycle posture signal: " + lifecycle.signalKey()
            ));

            if (applied != null) {
                matches.add(new MatchDraft(
                        "TRUST_POLICY",
                        null,
                        null,
                        applied.policy().getId(),
                        lifecycle.masterId(),
                        lifecycle.state(),
                        null,
                        null,
                        null,
                        delta,
                        toJson(Map.of("signal_key", lifecycle.signalKey()))
                ));
            }
        }

        short before = safeShort(profile.getCurrentScore(), (short) 100);
        short running = before;
        for (ScoreSignal signal : signals) {
            running = clampScore(running + signal.scoreDelta());
        }
        short after = running;
        short deltaTotal = (short) (after - before);

        Optional<TrustScoreDecisionPolicy> decisionPolicy = trustScoreDecisionPolicyRepository.findActivePolicyForScore(parsed.tenantId(), after, now);
        String decisionAction = decisionPolicy
                .map(TrustScoreDecisionPolicy::getDecisionAction)
                .map(x -> x.toUpperCase(Locale.ROOT))
                .orElseGet(() -> defaultDecisionForScore(after));

        boolean remediationRequired = decisionPolicy
                .map(TrustScoreDecisionPolicy::isRemediationRequired)
                .orElse(!"ALLOW".equalsIgnoreCase(decisionAction));

        String decisionReason = decisionPolicy
                .map(TrustScoreDecisionPolicy::getResponseMessage)
                .filter(x -> !x.isBlank())
                .orElse("Auto decision from evaluated trust score");

        return new EvaluationComputation(
                before,
                after,
                deltaTotal,
                matchedRuleCount,
                matchedAppCount,
                decisionAction,
                decisionReason,
                remediationRequired,
                decisionPolicy.map(TrustScoreDecisionPolicy::getId).orElse(null),
                matches,
                signals
        );
    }

    private List<SystemInformationRule> activeSystemRules(ParsedPosture parsed, OffsetDateTime now) {
        String cacheKey = parsed.tenantId() + "|" + parsed.osType();
        List<SystemInformationRule> rules = getCached(
                systemRuleCache,
                cacheKey,
                () -> List.copyOf(systemRuleRepository.findActiveForEvaluation(parsed.tenantId(), parsed.osType(), now))
        );
        return rules.stream()
                .filter(x -> equalsIgnoreCase(x.getOsType(), parsed.osType()))
                .filter(x -> x.getOsName() == null || x.getOsName().isBlank() || equalsIgnoreCase(x.getOsName(), parsed.osName()))
                .filter(x -> x.getDeviceType() == null || x.getDeviceType().isBlank() || equalsIgnoreCase(x.getDeviceType(), parsed.deviceType()))
                .sorted(Comparator.comparingInt(x -> x.getPriority() == null ? Integer.MAX_VALUE : x.getPriority()))
                .toList();
    }

    private Map<Long, List<SystemInformationRuleCondition>> activeRuleConditions(List<SystemInformationRule> activeRules) {
        if (activeRules.isEmpty()) {
            return Map.of();
        }
        List<Long> ruleIds = activeRules.stream()
                .map(SystemInformationRule::getId)
                .filter(Objects::nonNull)
                .toList();
        if (ruleIds.isEmpty()) {
            return Map.of();
        }
        String cacheKey = ruleIds.stream()
                .map(String::valueOf)
                .collect(java.util.stream.Collectors.joining(","));
        return getCached(
                ruleConditionCache,
                cacheKey,
                () -> {
                    List<SystemInformationRuleCondition> all = conditionRepository.findActiveByRuleIds(ruleIds);
                    Map<Long, List<SystemInformationRuleCondition>> out = new HashMap<>();
                    for (SystemInformationRuleCondition c : all) {
                        out.computeIfAbsent(c.getSystemInformationRuleId(), _ -> new ArrayList<>()).add(c);
                    }
                    out.replaceAll((_, value) -> List.copyOf(value));
                    return Map.copyOf(out);
                }
        );
    }

    private List<RejectApplication> activeRejectApps(String tenantId, String osType, OffsetDateTime now) {
        String cacheKey = tenantId + "|" + osType;
        return getCached(
                rejectApplicationCache,
                cacheKey,
                () -> List.copyOf(rejectApplicationRepository.findActiveForEvaluation(tenantId, osType, now))
        );
    }

    private List<TrustScorePolicy> activeTrustPolicies(String tenantId, OffsetDateTime now) {
        return getCached(
                trustPolicyCache,
                String.valueOf(tenantId),
                () -> List.copyOf(trustScorePolicyRepository.findActiveForEvaluation(tenantId, now))
        );
    }

    private <T> T getCached(ConcurrentMap<String, CacheEntry<T>> cache, String key, Supplier<T> loader) {
        long nowMs = System.currentTimeMillis();
        CacheEntry<T> cached = cache.get(key);
        if (cached != null && cached.expiresAtEpochMillis() > nowMs) {
            return cached.value();
        }
        T loaded = loader.get();
        long ttlMillis = Math.max(0L, referenceCacheSeconds) * 1000L;
        if (ttlMillis <= 0L) {
            cache.remove(key);
            return loaded;
        }
        cache.put(key, new CacheEntry<>(nowMs + ttlMillis, loaded));
        return loaded;
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

        String mode = normalizeUpper(rule.getMatchMode());
        if ("ANY".equals(mode)) {
            return groupResults.stream().anyMatch(Boolean::booleanValue);
        }
        return groupResults.stream().allMatch(Boolean::booleanValue);
    }

    private boolean evaluateCondition(SystemInformationRuleCondition condition, ParsedPosture parsed) {
        String field = trimToNull(condition.getFieldName());
        String operator = normalizeUpper(condition.getOperator());
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
                JsonNode json = objectMapper.readTree(c.getValueJson());
                if (json.isArray()) {
                    List<String> values = new ArrayList<>();
                    json.forEach(x -> values.add(stringifyNode(x)));
                    return values;
                }
                return stringifyNode(json);
            } catch (Exception _) {
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
        Collection<String> values = expectedCollection(expected);
        boolean contains = values.stream().anyMatch(v -> equalsIgnoreCase(v, String.valueOf(actual)));
        return positive == contains;
    }

    private boolean regexMatch(Object actual, Object expected) {
        if (actual == null || expected == null) {
            return false;
        }
        try {
            return Pattern.compile(String.valueOf(expected)).matcher(String.valueOf(actual)).find();
        } catch (Exception _) {
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

        Double actualNum = toDouble(actual);
        Double expectedNum = toDouble(expected);
        if (actualNum != null && expectedNum != null) {
            return Double.compare(actualNum, expectedNum);
        }
        if (actual instanceof Boolean || expected instanceof Boolean) {
            boolean a = Boolean.parseBoolean(String.valueOf(actual));
            boolean b = Boolean.parseBoolean(String.valueOf(expected));
            return Boolean.compare(a, b);
        }
        return String.valueOf(actual).compareToIgnoreCase(String.valueOf(expected));
    }

    private AppliedPolicy findAppliedPolicy(List<TrustScorePolicy> policies,
                                            String sourceType,
                                            List<String> signalCandidates,
                                            Short severity,
                                            String complianceAction,
                                            String tenantId) {
        List<TrustScorePolicy> matches = policies.stream()
                .filter(x -> equalsIgnoreCase(x.getSourceType(), sourceType))
                .filter(x -> signalCandidates.stream().anyMatch(sig -> sig != null && equalsIgnoreCase(x.getSignalKey(), sig)))
                .filter(x -> x.getSeverity() == null || Objects.equals(x.getSeverity(), severity))
                .filter(x -> x.getComplianceAction() == null || x.getComplianceAction().isBlank()
                        || equalsIgnoreCase(x.getComplianceAction(), complianceAction))
                .sorted(Comparator
                        .comparingInt((TrustScorePolicy x) -> scopePriority(x.getTenantId(), tenantId))
                        .thenComparing((TrustScorePolicy x) -> x.getWeight() == null ? 1.0 : x.getWeight(), Comparator.reverseOrder())
                        .thenComparing(TrustScorePolicy::getId))
                .toList();

        if (matches.isEmpty()) {
            return null;
        }
        return new AppliedPolicy(matches.getFirst());
    }

    private boolean matchesRejectApp(DeviceInstalledApplication app, RejectApplication reject) {
        if (!equalsIgnoreCase(app.getAppOsType(), reject.getAppOsType())) {
            return false;
        }

        boolean identityMatch = false;
        if (reject.getPackageId() != null && !reject.getPackageId().isBlank()) {
            identityMatch = equalsIgnoreCase(reject.getPackageId(), app.getPackageId());
        } else if (reject.getAppName() != null && !reject.getAppName().isBlank()) {
            identityMatch = equalsIgnoreCase(reject.getAppName(), app.getAppName());
        }

        if (!identityMatch) {
            return false;
        }

        if (reject.getMinAllowedVersion() != null && !reject.getMinAllowedVersion().isBlank()) {
            if (app.getAppVersion() == null || app.getAppVersion().isBlank()) {
                return true;
            }
            return compareVersion(app.getAppVersion(), reject.getMinAllowedVersion()) < 0;
        }
        return true;
    }

    private short weightedDelta(TrustScorePolicy policy) {
        double weight = policy.getWeight() == null ? 1.0 : policy.getWeight();
        int computed = (int) Math.round(policy.getScoreDelta() * weight);
        if (computed > 1000) {
            computed = 1000;
        }
        if (computed < -1000) {
            computed = -1000;
        }
        return (short) computed;
    }

    private short defaultRejectDelta(short severity) {
        return (short) Math.max(-80, -10 * severity);
    }

    private short defaultLifecycleDelta(String state) {
        return switch (normalizeUpper(state)) {
            case "EEOL" -> -40;
            case "EOL" -> -25;
            case "NOT_TRACKED" -> -15;
            case null -> (short) 0;
            default -> 0;
        };
    }

    private String defaultDecisionForScore(short score) {
        if (score < 40) {
            return "BLOCK";
        }
        if (score < 60) {
            return "QUARANTINE";
        }
        if (score < 80) {
            return "NOTIFY";
        }
        return "ALLOW";
    }

    private String normalizeDecision(String value) {
        String normalized = normalizeUpper(value);
        if (normalized == null || !DECISION_ACTIONS.contains(normalized)) {
            return null;
        }
        return normalized;
    }

    private int scopePriority(String policyTenantId, String tenantId) {
        String recordTenant = normalizeOptionalTenantId(policyTenantId);
        String normalizedTenant = normalizeOptionalTenantId(tenantId);
        if (normalizedTenant != null && Objects.equals(recordTenant, normalizedTenant)) {
            return 0;
        }
        if (recordTenant == null) {
            return 1;
        }
        return 2;
    }

    private String normalizeOptionalTenantId(String tenantId) {
        if (tenantId == null) {
            return null;
        }
        String normalized = tenantId.trim().toLowerCase(Locale.ROOT);
        return normalized.isBlank() ? null : normalized;
    }

    private short clampScore(int value) {
        if (value < 0) {
            return 0;
        }
        if (value > 100) {
            return 100;
        }
        return (short) value;
    }

    private int compareVersion(String left, String right) {
        String[] a = left.split("[._-]");
        String[] b = right.split("[._-]");
        int len = Math.max(a.length, b.length);
        for (int i = 0; i < len; i++) {
            String av = i < a.length ? a[i] : "0";
            String bv = i < b.length ? b[i] : "0";
            Integer ai = parseIntOrNull(av);
            Integer bi = parseIntOrNull(bv);
            int cmp;
            if (ai != null && bi != null) {
                cmp = Integer.compare(ai, bi);
            } else {
                cmp = av.compareToIgnoreCase(bv);
            }
            if (cmp != 0) {
                return cmp;
            }
        }
        return 0;
    }

    private Optional<OffsetDateTime> parseOffsetDateTime(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(OffsetDateTime.parse(value.trim()));
        } catch (Exception _) {
            return Optional.empty();
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception _) {
            throw new RuntimeException("JSON serialization failed");
        }
    }

}
