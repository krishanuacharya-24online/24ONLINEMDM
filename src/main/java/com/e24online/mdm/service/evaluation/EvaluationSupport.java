package com.e24online.mdm.service.evaluation;

import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.util.*;

import static java.util.Collections.emptyList;

@Service
public class EvaluationSupport {

    private static final Set<String> DECISION_ACTIONS = Set.of("ALLOW", "QUARANTINE", "BLOCK", "NOTIFY");
    private final ObjectMapper objectMapper;

    public EvaluationSupport(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Short safeShort(Number value, Short defaultValue) {
        return value == null ? defaultValue : value.shortValue();
    }

    public String safeText(String value) {
        return value == null ? "" : value;
    }

    public String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public boolean equalsIgnoreCase(String left, String right) {
        return Objects.equals(normalizeUpper(left), normalizeUpper(right));
    }

    public String normalizeUpper(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? null : trimmed.toUpperCase(Locale.ROOT);
    }

    public Collection<String> expectedCollection(Object expected) {
        if (expected == null) {
            return emptyList();
        }
        if (expected instanceof Collection<?> collection) {
            List<String> out = new ArrayList<>(collection.size());
            for (Object value : collection) {
                if (value != null) {
                    out.add(String.valueOf(value));
                }
            }
            return out;
        }
        return List.of(String.valueOf(expected));
    }

    public Double toDouble(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (Exception _) {
            return null;
        }
    }

    public String stringifyNode(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isNumber() || node.isBoolean()) {
            return node.asText();
        }
        return node.toString();
    }

    public short weightedDelta(com.e24online.mdm.domain.TrustScorePolicy policy) {
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

    public short defaultRejectDelta(short severity) {
        return (short) Math.max(-80, -10 * severity);
    }

    public short defaultLifecycleDelta(String state) {
        return switch (normalizeUpper(state)) {
            case "EEOL" -> -40;
            case "EOL" -> -25;
            case "NOT_TRACKED" -> -15;
            case null -> (short) 0;
            default -> 0;
        };
    }

    public String defaultDecisionForScore(short score) {
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

    public String normalizeDecision(String value) {
        String normalized = normalizeUpper(value);
        if (normalized == null || !DECISION_ACTIONS.contains(normalized)) {
            return null;
        }
        return normalized;
    }

    public int scopePriority(String policyTenantId, String tenantId) {
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

    public String normalizeOptionalTenantId(String tenantId) {
        if (tenantId == null) {
            return null;
        }
        String normalized = tenantId.trim().toLowerCase(Locale.ROOT);
        return normalized.isBlank() ? null : normalized;
    }

    public short clampScore(int value) {
        if (value < 0) {
            return 0;
        }
        if (value > 100) {
            return 100;
        }
        return (short) value;
    }

    public int compareVersion(String left, String right) {
        String[] a = left.split("[._-]");
        String[] b = right.split("[._-]");
        int len = Math.max(a.length, b.length);
        for (int i = 0; i < len; i++) {
            String av = i < a.length ? a[i] : "0";
            String bv = i < b.length ? b[i] : "0";
            Integer ai = parseIntOrNull(av);
            Integer bi = parseIntOrNull(bv);
            int cmp = (ai != null && bi != null) ? Integer.compare(ai, bi) : av.compareToIgnoreCase(bv);
            if (cmp != 0) {
                return cmp;
            }
        }
        return 0;
    }

    public Integer parseIntOrNull(String value) {
        try {
            return Integer.parseInt(value);
        } catch (Exception _) {
            return null;
        }
    }

    public Optional<OffsetDateTime> parseOffsetDateTime(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(OffsetDateTime.parse(value.trim()));
        } catch (Exception _) {
            return Optional.empty();
        }
    }

    public String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new RuntimeException("JSON serialization failed", ex);
        }
    }

    public ObjectMapper objectMapper() {
        return objectMapper;
    }
}
