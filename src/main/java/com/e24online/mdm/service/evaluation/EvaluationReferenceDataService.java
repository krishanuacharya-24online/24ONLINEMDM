package com.e24online.mdm.service.evaluation;

import com.e24online.mdm.domain.RejectApplication;
import com.e24online.mdm.domain.SystemInformationRule;
import com.e24online.mdm.domain.SystemInformationRuleCondition;
import com.e24online.mdm.domain.TrustScorePolicy;
import com.e24online.mdm.records.cache.CacheEntry;
import com.e24online.mdm.records.posture.evaluation.ParsedPosture;
import com.e24online.mdm.repository.RejectApplicationRepository;
import com.e24online.mdm.repository.SystemInformationRuleConditionRepository;
import com.e24online.mdm.repository.SystemInformationRuleRepository;
import com.e24online.mdm.repository.TrustScorePolicyRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

@Service
public class EvaluationReferenceDataService {

    private static final long DEFAULT_REFERENCE_CACHE_SECONDS = 30L;

    private final SystemInformationRuleRepository systemRuleRepository;
    private final SystemInformationRuleConditionRepository conditionRepository;
    private final RejectApplicationRepository rejectApplicationRepository;
    private final TrustScorePolicyRepository trustScorePolicyRepository;
    private final EvaluationSupport support;
    private final ConcurrentMap<String, CacheEntry<List<SystemInformationRule>>> systemRuleCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, CacheEntry<Map<Long, List<SystemInformationRuleCondition>>>> ruleConditionCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, CacheEntry<List<RejectApplication>>> rejectApplicationCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, CacheEntry<List<TrustScorePolicy>>> trustPolicyCache = new ConcurrentHashMap<>();

    @Value("${mdm.evaluation.reference-cache-seconds:30}")
    private long referenceCacheSeconds = DEFAULT_REFERENCE_CACHE_SECONDS;

    public EvaluationReferenceDataService(SystemInformationRuleRepository systemRuleRepository,
                                   SystemInformationRuleConditionRepository conditionRepository,
                                   RejectApplicationRepository rejectApplicationRepository,
                                   TrustScorePolicyRepository trustScorePolicyRepository,
                                   EvaluationSupport support) {
        this.systemRuleRepository = systemRuleRepository;
        this.conditionRepository = conditionRepository;
        this.rejectApplicationRepository = rejectApplicationRepository;
        this.trustScorePolicyRepository = trustScorePolicyRepository;
        this.support = support;
    }

    public List<SystemInformationRule> activeSystemRules(ParsedPosture parsed, OffsetDateTime now) {
        String cacheKey = String.valueOf(parsed.tenantId());
        List<SystemInformationRule> rules = getCached(
                systemRuleCache,
                cacheKey,
                () -> List.copyOf(systemRuleRepository.findActiveForEvaluation(parsed.tenantId(), now))
        );
        return rules.stream()
                .filter(x -> x.getOsType() == null || x.getOsType().isBlank() || support.equalsIgnoreCase(x.getOsType(), parsed.osType()))
                .filter(x -> x.getOsName() == null || x.getOsName().isBlank() || support.equalsIgnoreCase(x.getOsName(), parsed.osName()))
                .filter(x -> x.getDeviceType() == null || x.getDeviceType().isBlank() || support.equalsIgnoreCase(x.getDeviceType(), parsed.deviceType()))
                .sorted(Comparator.comparingInt(x -> x.getPriority() == null ? Integer.MAX_VALUE : x.getPriority()))
                .toList();
    }

    public Map<Long, List<SystemInformationRuleCondition>> activeRuleConditions(List<SystemInformationRule> activeRules) {
        if (activeRules.isEmpty()) {
            return Map.of();
        }
        List<Long> ruleIds = activeRules.stream().map(SystemInformationRule::getId).filter(Objects::nonNull).toList();
        if (ruleIds.isEmpty()) {
            return Map.of();
        }
        String cacheKey = ruleIds.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
        return getCached(ruleConditionCache, cacheKey, () -> {
            List<SystemInformationRuleCondition> all = conditionRepository.findActiveByRuleIds(ruleIds);
            Map<Long, List<SystemInformationRuleCondition>> out = new HashMap<>();
            for (SystemInformationRuleCondition c : all) {
                out.computeIfAbsent(c.getSystemInformationRuleId(), _ -> new ArrayList<>()).add(c);
            }
            out.replaceAll((_, value) -> List.copyOf(value));
            return Map.copyOf(out);
        });
    }

    public List<RejectApplication> activeRejectApps(String tenantId, OffsetDateTime now) {
        return getCached(rejectApplicationCache, String.valueOf(tenantId), () -> List.copyOf(rejectApplicationRepository.findActiveForEvaluation(tenantId, now)));
    }

    public List<TrustScorePolicy> activeTrustPolicies(String tenantId, OffsetDateTime now) {
        return getCached(trustPolicyCache, String.valueOf(tenantId), () -> List.copyOf(trustScorePolicyRepository.findActiveForEvaluation(tenantId, now)));
    }

    public <T> T getCached(ConcurrentMap<String, CacheEntry<T>> cache, String key, Supplier<T> loader) {
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
}
