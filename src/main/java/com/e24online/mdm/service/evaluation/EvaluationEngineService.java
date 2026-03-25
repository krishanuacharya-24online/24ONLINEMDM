package com.e24online.mdm.service.evaluation;

import com.e24online.mdm.domain.DeviceInstalledApplication;
import com.e24online.mdm.domain.DeviceTrustProfile;
import com.e24online.mdm.records.posture.evaluation.EvaluationComputation;
import com.e24online.mdm.records.posture.evaluation.LifecycleResolution;
import com.e24online.mdm.records.posture.evaluation.MatchDraft;
import com.e24online.mdm.records.posture.evaluation.ParsedPosture;
import com.e24online.mdm.records.posture.evaluation.ScoreSignal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class EvaluationEngineService {

    private static final Logger log = LoggerFactory.getLogger(EvaluationEngineService.class);

    private final EvaluationReferenceDataService referenceDataService;
    private final SystemRuleEvaluator systemRuleEvaluator;
    private final ApplicationRuleEvaluator applicationRuleEvaluator;
    private final TrustScoreCalculator trustScoreCalculator;
    private final TrustDecisionMaker trustDecisionMaker;
    private final EvaluationSupport support;

    public EvaluationEngineService(EvaluationReferenceDataService referenceDataService,
                                   SystemRuleEvaluator systemRuleEvaluator,
                                   ApplicationRuleEvaluator applicationRuleEvaluator,
                                   TrustScoreCalculator trustScoreCalculator,
                                   TrustDecisionMaker trustDecisionMaker,
                                   EvaluationSupport support) {
        this.referenceDataService = referenceDataService;
        this.systemRuleEvaluator = systemRuleEvaluator;
        this.applicationRuleEvaluator = applicationRuleEvaluator;
        this.trustScoreCalculator = trustScoreCalculator;
        this.trustDecisionMaker = trustDecisionMaker;
        this.support = support;
    }

    public EvaluationComputation computeEvaluation(DeviceTrustProfile profile,
                                                   ParsedPosture parsed,
                                                   List<DeviceInstalledApplication> installedApps,
                                                   LifecycleResolution lifecycle,
                                                   OffsetDateTime now) {
        log.debug("computeEvaluation method");
        log.debug("computeEvaluation tenantId={}, osType={}, osTypeNull={}", parsed.tenantId(), parsed.osType(), parsed.osType() == null);

        var activeRules = referenceDataService.activeSystemRules(parsed, now);
        var conditionsByRule = referenceDataService.activeRuleConditions(activeRules);
        var activeRejectApps = referenceDataService.activeRejectApps(parsed.tenantId(), now);
        var activePolicies = referenceDataService.activeTrustPolicies(parsed.tenantId(), now);

        var systemResult = systemRuleEvaluator.evaluate(activeRules, conditionsByRule, activePolicies, parsed, lifecycle);
        var appResult = applicationRuleEvaluator.evaluate(installedApps, activeRejectApps, activePolicies, parsed, lifecycle);
        var lifecycleResult = trustScoreCalculator.evaluateLifecycleSignal(activePolicies, parsed.tenantId(), lifecycle);

        List<MatchDraft> matches = new ArrayList<>();
        matches.addAll(systemResult.matches());
        matches.addAll(appResult.matches());
        matches.addAll(lifecycleResult.matches());

        List<ScoreSignal> signals = new ArrayList<>();
        signals.addAll(systemResult.signals());
        signals.addAll(appResult.signals());
        signals.addAll(lifecycleResult.signals());

        short before = support.safeShort(profile.getCurrentScore(), (short) 100);
        var scoreSummary = trustScoreCalculator.compute(before, signals);
        var decision = trustDecisionMaker.decide(parsed.tenantId(), scoreSummary.after(), now);

        return new EvaluationComputation(
                scoreSummary.before(),
                scoreSummary.after(),
                scoreSummary.deltaTotal(),
                systemResult.matchedRuleCount(),
                appResult.matchedAppCount(),
                decision.decisionAction(),
                decision.decisionReason(),
                decision.remediationRequired(),
                decision.decisionPolicyId(),
                matches,
                signals
        );
    }
}
