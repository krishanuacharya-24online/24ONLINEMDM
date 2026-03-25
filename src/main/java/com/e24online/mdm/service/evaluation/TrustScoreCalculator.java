package com.e24online.mdm.service.evaluation;

import com.e24online.mdm.records.posture.evaluation.LifecycleResolution;
import com.e24online.mdm.records.posture.evaluation.MatchDraft;
import com.e24online.mdm.records.posture.evaluation.ScoreSignal;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class TrustScoreCalculator {

    private final EvaluationSupport support;
    private final TrustPolicyResolver trustPolicyResolver;

    public TrustScoreCalculator(EvaluationSupport support,
                         TrustPolicyResolver trustPolicyResolver) {
        this.support = support;
        this.trustPolicyResolver = trustPolicyResolver;
    }

    public LifecycleScoreEvaluation evaluateLifecycleSignal(List<com.e24online.mdm.domain.TrustScorePolicy> activePolicies,
                                                     String tenantId,
                                                     LifecycleResolution lifecycle) {
        List<MatchDraft> matches = new ArrayList<>();
        List<ScoreSignal> signals = new ArrayList<>();
        if (!"SUPPORTED".equalsIgnoreCase(lifecycle.state())) {
            var applied = trustPolicyResolver.findAppliedPolicy(
                    activePolicies,
                    "POSTURE_SIGNAL",
                    trustPolicyResolver.signalCandidates(lifecycle.signalKey()),
                    null,
                    null,
                    tenantId
            );
            short delta = applied != null ? support.weightedDelta(applied.policy()) : support.defaultLifecycleDelta(lifecycle.state());
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
                        support.toJson(Map.of("signal_key", lifecycle.signalKey()))
                ));
            }
        }
        return new LifecycleScoreEvaluation(matches, signals);
    }

    public ScoreSummary compute(short before, List<ScoreSignal> signals) {
        short running = before;
        for (ScoreSignal signal : signals) {
            running = support.clampScore(running + signal.scoreDelta());
        }
        short after = running;
        short deltaTotal = (short) (after - before);
        return new ScoreSummary(before, after, deltaTotal);
    }

    public record LifecycleScoreEvaluation(List<MatchDraft> matches, List<ScoreSignal> signals) {
    }

    public record ScoreSummary(short before, short after, short deltaTotal) {
    }
}
