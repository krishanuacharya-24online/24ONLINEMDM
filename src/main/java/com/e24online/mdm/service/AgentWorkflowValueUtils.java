package com.e24online.mdm.service;

import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

final class AgentWorkflowValueUtils {

    private AgentWorkflowValueUtils() {
    }

    static String safeText(String value) {
        return value == null ? "" : value.trim();
    }

    static String truncate(String value, int maxLen) {
        if (value == null) {
            return null;
        }
        if (maxLen <= 0) {
            return "";
        }
        return value.length() <= maxLen ? value : value.substring(0, maxLen);
    }

    static boolean equalsIgnoreCase(String a, String b) {
        if (a == null && b == null) {
            return true;
        }
        return a != null && a.equalsIgnoreCase(b);
    }

    static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    static String normalizeUpper(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? null : trimmed.toUpperCase(Locale.ROOT);
    }

    static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    static String trimToNullAndCap(String value, int maxLen) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            return null;
        }
        if (maxLen <= 0) {
            return "";
        }
        return trimmed.length() <= maxLen ? trimmed : trimmed.substring(0, maxLen);
    }

    static String text(JsonNode node, String field) {
        if (node == null || field == null) {
            return null;
        }
        JsonNode child = node.get(field);
        if (child == null || child.isNull()) {
            return null;
        }
        String value = child.asText(null);
        return trimToNull(value);
    }

    static Integer intValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isInt() || node.isLong()) {
            return node.intValue();
        }
        if (node.isTextual()) {
            return parseIntOrNull(node.asText());
        }
        return null;
    }

    static Boolean boolValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isBoolean()) {
            return node.booleanValue();
        }
        if (node.isTextual()) {
            String text = node.asText();
            if (text == null) {
                return null;
            }
            String normalized = text.trim().toLowerCase(Locale.ROOT);
            if ("true".equals(normalized)) {
                return true;
            }
            if ("false".equals(normalized)) {
                return false;
            }
        }
        return null;
    }

    static Integer parseIntOrNull(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException _) {
            return null;
        }
    }

    static Double toDouble(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text) {
            try {
                return Double.parseDouble(text.trim());
            } catch (NumberFormatException _) {
                return null;
            }
        }
        return null;
    }

    static String stringifyNode(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return trimToNull(node.asText());
        }
        return trimToNull(node.toString());
    }

    static Collection<String> expectedCollection(Object expected) {
        List<String> values = new ArrayList<>();
        if (expected instanceof Collection<?> collection) {
            for (Object item : collection) {
                String normalized = normalizeUpper(item == null ? null : item.toString());
                if (normalized != null) {
                    values.add(normalized);
                }
            }
            return values;
        }
        if (expected != null) {
            String normalized = normalizeUpper(expected.toString());
            if (normalized != null) {
                values.add(normalized);
            }
        }
        return values;
    }

    static Short safeShort(Short value, Short fallback) {
        return value != null ? value : fallback;
    }
}
