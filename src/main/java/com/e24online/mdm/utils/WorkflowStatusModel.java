package com.e24online.mdm.utils;

import java.util.Set;

public final class WorkflowStatusModel {

    public static final Set<String> PAYLOAD_PROCESS_STATUSES =
            Set.of("RECEIVED", "QUEUED", "VALIDATED", "EVALUATED", "FAILED");
    public static final Set<String> EVALUATION_CANCELLABLE_STATUSES =
            Set.of("QUEUED", "IN_PROGRESS", "VALIDATING", "RUNNING");
    public static final Set<String> EVALUATION_TERMINAL_STATUSES =
            Set.of("COMPLETED", "FAILED", "CANCELLED");
    public static final Set<String> DELIVERY_STATUSES =
            Set.of("PENDING", "DELIVERED", "ACKNOWLEDGED", "FAILED", "TIMEOUT");
    public static final Set<String> REMEDIATION_STATUSES =
            Set.of("PROPOSED", "DELIVERED", "USER_ACKNOWLEDGED", "STILL_OPEN", "RESOLVED_ON_RESCAN", "CLOSED");
    public static final Set<String> OPEN_REMEDIATION_STATUSES =
            Set.of("PROPOSED", "DELIVERED", "USER_ACKNOWLEDGED", "STILL_OPEN");
    public static final Set<String> REMEDIATION_COMPLETION_STATUSES =
            Set.of("USER_ACKNOWLEDGED", "RESOLVED_ON_RESCAN", "CLOSED");

    private WorkflowStatusModel() {
    }

    public static String canonicalDeliveryStatus(String value) {
        String normalized = AgentWorkflowValueUtils.normalizeUpper(value);
        if (normalized == null) {
            return null;
        }
        return switch (normalized) {
            case "SENT" -> "DELIVERED";
            case "ACKED" -> "ACKNOWLEDGED";
            default -> normalized;
        };
    }

    public static boolean isValidDeliveryStatus(String value) {
        String normalized = canonicalDeliveryStatus(value);
        return normalized != null && DELIVERY_STATUSES.contains(normalized);
    }

    public static boolean isDeliveryAttemptStatus(String value) {
        String normalized = canonicalDeliveryStatus(value);
        return normalized != null && !"PENDING".equals(normalized);
    }

    public static boolean isAcknowledgedDeliveryStatus(String value) {
        return "ACKNOWLEDGED".equals(canonicalDeliveryStatus(value));
    }

    public static String canonicalRemediationStatus(String value) {
        String normalized = AgentWorkflowValueUtils.normalizeUpper(value);
        if (normalized == null) {
            return null;
        }
        return switch (normalized) {
            case "PENDING" -> "PROPOSED";
            case "SENT" -> "DELIVERED";
            case "ACKED", "ACKNOWLEDGED" -> "USER_ACKNOWLEDGED";
            case "FAILED" -> "STILL_OPEN";
            case "SKIPPED" -> "CLOSED";
            default -> normalized;
        };
    }

    public static boolean isValidRemediationStatus(String value) {
        String normalized = canonicalRemediationStatus(value);
        return normalized != null && REMEDIATION_STATUSES.contains(normalized);
    }

    public static boolean isOpenRemediationStatus(String value) {
        String normalized = canonicalRemediationStatus(value);
        return normalized != null && OPEN_REMEDIATION_STATUSES.contains(normalized);
    }

    public static boolean isCompletionTrackedRemediationStatus(String value) {
        String normalized = canonicalRemediationStatus(value);
        return normalized != null && REMEDIATION_COMPLETION_STATUSES.contains(normalized);
    }
}
