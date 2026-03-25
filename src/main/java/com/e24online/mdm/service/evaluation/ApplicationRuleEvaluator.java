package com.e24online.mdm.service;

import com.e24online.mdm.domain.DeviceInstalledApplication;
import com.e24online.mdm.domain.RejectApplication;
import com.e24online.mdm.domain.TrustScorePolicy;
import com.e24online.mdm.records.posture.evaluation.AppliedPolicy;
import com.e24online.mdm.records.posture.evaluation.LifecycleResolution;
import com.e24online.mdm.records.posture.evaluation.MatchDraft;
import com.e24online.mdm.records.posture.evaluation.ParsedPosture;
import com.e24online.mdm.records.posture.evaluation.ScoreSignal;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
class ApplicationRuleEvaluator {

    private final EvaluationSupport support;
    private final TrustPolicyResolver trustPolicyResolver;

    ApplicationRuleEvaluator(EvaluationSupport support,
                             TrustPolicyResolver trustPolicyResolver) {
        this.support = support;
        this.trustPolicyResolver = trustPolicyResolver;
    }

    ApplicationRuleEvaluation evaluate(List<DeviceInstalledApplication> installedApps,
                                       List<RejectApplication> activeRejectApps,
                                       List<TrustScorePolicy> activePolicies,
                                       ParsedPosture parsed,
                                       LifecycleResolution lifecycle) {
        List<MatchDraft> matches = new ArrayList<>();
        List<ScoreSignal> signals = new ArrayList<>();
        int matchedAppCount = 0;
        for (DeviceInstalledApplication app : installedApps) {
            for (RejectApplication reject : activeRejectApps) {
                if (!matchesRejectApp(app, reject)) {
                    continue;
                }
                AppliedPolicy applied = trustPolicyResolver.findAppliedPolicy(
                        activePolicies,
                        "REJECT_APPLICATION",
                        trustPolicyResolver.signalCandidates(reject.getPackageId(), reject.getAppName(), reject.getPolicyTag()),
                        reject.getSeverity(),
                        "BLOCK",
                        parsed.tenantId()
                );
                short delta = applied != null ? support.weightedDelta(applied.policy()) : support.defaultRejectDelta(support.safeShort(reject.getSeverity(), (short) 3));
                matches.add(new MatchDraft(
                        "REJECT_APPLICATION",
                        null,
                        reject.getId(),
                        null,
                        null,
                        lifecycle.state(),
                        app.getId(),
                        support.safeShort(reject.getSeverity(), null),
                        "BLOCK",
                        delta,
                        support.toJson(Map.of("app_name", support.safeText(app.getAppName()), "package_id", support.safeText(app.getPackageId())))
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
                        "Matched rejected app: " + support.safeText(app.getAppName())
                ));
                matchedAppCount++;
            }
        }
        return new ApplicationRuleEvaluation(matches, signals, matchedAppCount);
    }

    private boolean matchesRejectApp(DeviceInstalledApplication app, RejectApplication reject) {
        if (reject.getAppOsType() != null && !reject.getAppOsType().isBlank()
                && !support.equalsIgnoreCase(app.getAppOsType(), reject.getAppOsType())) {
            return false;
        }
        boolean identityMatch = false;
        if (reject.getPackageId() != null && !reject.getPackageId().isBlank()) {
            identityMatch = support.equalsIgnoreCase(reject.getPackageId(), app.getPackageId());
        } else if (reject.getAppName() != null && !reject.getAppName().isBlank()) {
            identityMatch = support.equalsIgnoreCase(reject.getAppName(), app.getAppName());
        }
        if (!identityMatch) {
            return false;
        }
        if (reject.getMinAllowedVersion() != null && !reject.getMinAllowedVersion().isBlank()) {
            if (app.getAppVersion() == null || app.getAppVersion().isBlank()) {
                return true;
            }
            return support.compareVersion(app.getAppVersion(), reject.getMinAllowedVersion()) < 0;
        }
        return true;
    }

    record ApplicationRuleEvaluation(List<MatchDraft> matches, List<ScoreSignal> signals, int matchedAppCount) {
    }
}
