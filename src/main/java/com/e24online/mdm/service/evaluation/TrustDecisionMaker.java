package com.e24online.mdm.service.evaluation;

import com.e24online.mdm.domain.TrustScoreDecisionPolicy;
import com.e24online.mdm.repository.TrustScoreDecisionPolicyRepository;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Optional;

@Service
public class TrustDecisionMaker {

    private final TrustScoreDecisionPolicyRepository trustScoreDecisionPolicyRepository;
    private final EvaluationSupport support;

    public TrustDecisionMaker(TrustScoreDecisionPolicyRepository trustScoreDecisionPolicyRepository,
                       EvaluationSupport support) {
        this.trustScoreDecisionPolicyRepository = trustScoreDecisionPolicyRepository;
        this.support = support;
    }

    public DecisionResult decide(String tenantId, short after, OffsetDateTime now) {
        Optional<TrustScoreDecisionPolicy> decisionPolicy = trustScoreDecisionPolicyRepository.findActivePolicyForScore(tenantId, after, now);
        String decisionAction = decisionPolicy
                .map(TrustScoreDecisionPolicy::getDecisionAction)
                .map(x -> x.toUpperCase(Locale.ROOT))
                .orElseGet(() -> support.defaultDecisionForScore(after));
        boolean remediationRequired = decisionPolicy
                .map(TrustScoreDecisionPolicy::isRemediationRequired)
                .orElse(!"ALLOW".equalsIgnoreCase(decisionAction));
        String decisionReason = decisionPolicy
                .map(TrustScoreDecisionPolicy::getResponseMessage)
                .filter(x -> !x.isBlank())
                .orElse("Auto decision from evaluated trust score");
        return new DecisionResult(
                decisionAction,
                decisionReason,
                remediationRequired,
                decisionPolicy.map(TrustScoreDecisionPolicy::getId).orElse(null)
        );
    }

    public record DecisionResult(String decisionAction, String decisionReason, boolean remediationRequired, Long decisionPolicyId) {
    }
}
