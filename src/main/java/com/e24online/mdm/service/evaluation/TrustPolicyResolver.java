package com.e24online.mdm.service.evaluation;

import com.e24online.mdm.domain.TrustScorePolicy;
import com.e24online.mdm.records.posture.evaluation.AppliedPolicy;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
public class TrustPolicyResolver {

    private final EvaluationSupport support;

    public TrustPolicyResolver(EvaluationSupport support) {
        this.support = support;
    }

    public AppliedPolicy findAppliedPolicy(List<TrustScorePolicy> policies,
                                    String sourceType,
                                    List<String> signalCandidates,
                                    Short severity,
                                    String complianceAction,
                                    String tenantId) {
        List<TrustScorePolicy> matches = policies.stream()
                .filter(x -> support.equalsIgnoreCase(x.getSourceType(), sourceType))
                .filter(x -> signalCandidates.stream().anyMatch(sig -> sig != null && support.equalsIgnoreCase(x.getSignalKey(), sig)))
                .filter(x -> x.getSeverity() == null || Objects.equals(x.getSeverity(), severity))
                .filter(x -> x.getComplianceAction() == null || x.getComplianceAction().isBlank() || support.equalsIgnoreCase(x.getComplianceAction(), complianceAction))
                .sorted(Comparator
                        .comparingInt((TrustScorePolicy x) -> support.scopePriority(x.getTenantId(), tenantId))
                        .thenComparing((TrustScorePolicy x) -> x.getWeight() == null ? 1.0 : x.getWeight(), Comparator.reverseOrder())
                        .thenComparing(TrustScorePolicy::getId))
                .toList();
        return matches.isEmpty() ? null : new AppliedPolicy(matches.getFirst());
    }

    public List<String> signalCandidates(String... values) {
        if (values == null || values.length == 0) {
            return List.of();
        }
        List<String> out = new ArrayList<>(values.length);
        for (String value : values) {
            String normalized = support.trimToNull(value);
            if (normalized != null) {
                out.add(normalized);
            }
        }
        return out.isEmpty() ? List.of() : List.copyOf(out);
    }
}
