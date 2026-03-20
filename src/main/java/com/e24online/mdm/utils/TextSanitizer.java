package com.e24online.mdm.utils;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.LinkedHashMap;
import java.util.Map;

public final class TextSanitizer {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String REPLACEMENT_CHAR = "\uFFFD";
    private static final String LATIN1_REPLACEMENT = "\u00EF\u00BF\u00BD";
    private static final String DOUBLE_ENCODED_REPLACEMENT = "\u00C3\u00AF\u00C2\u00BF\u00C2\u00BD";
    private static final String MOJIBAKE_ELLIPSIS = "\u00E2\u20AC\u00A6";
    private static final String DOUBLE_ENCODED_ELLIPSIS = "\u00C3\u00A2\u00E2\u201A\u00AC\u00C2\u00A6";
    private static final String MOJIBAKE_EN_DASH = "\u00E2\u20AC\u201C";
    private static final String DOUBLE_ENCODED_EN_DASH = "\u00C3\u00A2\u00E2\u201A\u00AC\u00E2\u20AC\u015C";
    private static final String MOJIBAKE_EM_DASH = "\u00E2\u20AC\u201D";
    private static final String DOUBLE_ENCODED_EM_DASH = "\u00C3\u00A2\u00E2\u201A\u00AC\u00E2\u20AC\u009D";
    private static final String MOJIBAKE_OPEN_QUOTE = "\u00E2\u20AC\u015C";
    private static final String DOUBLE_ENCODED_OPEN_QUOTE = "\u00C3\u00A2\u00E2\u201A\u00AC\u00C5\u201C";
    private static final String MOJIBAKE_CLOSE_QUOTE = "\u00E2\u20AC\uFFFD";
    private static final String DOUBLE_ENCODED_CLOSE_QUOTE = "\u00C3\u00A2\u00E2\u201A\u00AC\u00EF\u00BF\u00BD";

    private TextSanitizer() {
    }

    public static String sanitizeText(String value) {
        if (value == null) {
            return null;
        }
        String sanitized = value
                .replace(REPLACEMENT_CHAR, "")
                .replace(LATIN1_REPLACEMENT, "")
                .replace(DOUBLE_ENCODED_REPLACEMENT, "")
                .replace(MOJIBAKE_ELLIPSIS, "...")
                .replace(DOUBLE_ENCODED_ELLIPSIS, "...")
                .replace(MOJIBAKE_EN_DASH, "-")
                .replace(DOUBLE_ENCODED_EN_DASH, "-")
                .replace(MOJIBAKE_EM_DASH, "-")
                .replace(DOUBLE_ENCODED_EM_DASH, "-")
                .replace(MOJIBAKE_OPEN_QUOTE, "\"")
                .replace(DOUBLE_ENCODED_OPEN_QUOTE, "\"")
                .replace(MOJIBAKE_CLOSE_QUOTE, "\"")
                .replace(DOUBLE_ENCODED_CLOSE_QUOTE, "\"");
        return sanitized.strip();
    }

    public static boolean containsCorruption(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        return value.contains(REPLACEMENT_CHAR)
                || value.contains(LATIN1_REPLACEMENT)
                || value.contains(DOUBLE_ENCODED_REPLACEMENT)
                || value.contains(MOJIBAKE_ELLIPSIS)
                || value.contains(DOUBLE_ENCODED_ELLIPSIS)
                || value.contains(MOJIBAKE_EN_DASH)
                || value.contains(DOUBLE_ENCODED_EN_DASH)
                || value.contains(MOJIBAKE_EM_DASH)
                || value.contains(DOUBLE_ENCODED_EM_DASH)
                || value.contains(MOJIBAKE_OPEN_QUOTE)
                || value.contains(DOUBLE_ENCODED_OPEN_QUOTE)
                || value.contains(MOJIBAKE_CLOSE_QUOTE)
                || value.contains(DOUBLE_ENCODED_CLOSE_QUOTE);
    }

    public static String sanitizeAppDisplayName(String appName, String packageId) {
        String sanitizedPackageId = blankToNull(sanitizeText(packageId));
        String sanitizedAppName = blankToNull(sanitizeText(appName));
        if (containsCorruption(appName) && sanitizedPackageId != null) {
            return sanitizedPackageId;
        }
        if (sanitizedAppName == null && sanitizedPackageId != null) {
            return sanitizedPackageId;
        }
        return sanitizedAppName;
    }

    public static String sanitizeStructuredJson(String json) {
        if (json == null || json.isBlank()) {
            return sanitizeText(json);
        }
        try {
            JsonNode node = OBJECT_MAPPER.readTree(json);
            return OBJECT_MAPPER.writeValueAsString(sanitizeJsonNode(node));
        } catch (JacksonException ex) {
            return sanitizeText(json);
        }
    }

    public static JsonNode sanitizeJsonNode(JsonNode node) {
        if (node == null || node.isNull()) {
            return node;
        }
        if (node.isObject()) {
            ObjectNode sanitized = OBJECT_MAPPER.createObjectNode();
            ObjectNode objectNode = (ObjectNode) node;
            objectNode.properties().forEach(entry -> sanitized.set(entry.getKey(), sanitizeJsonNode(entry.getValue())));
            if (sanitized.has("app_name")) {
                JsonNode packageIdNode = objectNode.get("package_id");
                String packageId = packageIdNode != null && packageIdNode.isTextual() ? packageIdNode.asText() : null;
                JsonNode appNameNode = objectNode.get("app_name");
                String appName = appNameNode != null && appNameNode.isTextual() ? appNameNode.asText() : null;
                String resolvedName = sanitizeAppDisplayName(appName, packageId);
                if (resolvedName != null) {
                    sanitized.put("app_name", resolvedName);
                } else {
                    sanitized.remove("app_name");
                }
            }
            return sanitized;
        }
        if (node.isArray()) {
            ArrayNode sanitized = OBJECT_MAPPER.createArrayNode();
            for (JsonNode child : node) {
                sanitized.add(sanitizeJsonNode(child));
            }
            return sanitized;
        }
        if (node.isTextual()) {
            return OBJECT_MAPPER.getNodeFactory().textNode(sanitizeText(node.asText()));
        }
        return node.deepCopy();
    }

    public static Map<String, Object> sanitizeRow(Map<String, Object> row) {
        if (row == null || row.isEmpty()) {
            return row;
        }
        Map<String, Object> sanitized = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String text) {
                if (looksLikeJsonField(entry.getKey())) {
                    sanitized.put(entry.getKey(), sanitizeStructuredJson(text));
                } else {
                    sanitized.put(entry.getKey(), sanitizeText(text));
                }
            } else {
                sanitized.put(entry.getKey(), value);
            }
        }
        if (sanitized.containsKey("app_name")) {
            String appName = row.get("app_name") instanceof String text ? text : null;
            String packageId = row.get("package_id") instanceof String text ? text : null;
            sanitized.put("app_name", sanitizeAppDisplayName(appName, packageId));
        }
        return sanitized;
    }

    private static boolean looksLikeJsonField(String key) {
        if (key == null) {
            return false;
        }
        String normalized = key.strip().toLowerCase();
        return normalized.endsWith("_json")
                || "agent_capabilities".equals(normalized)
                || "validation_warnings".equals(normalized)
                || "response_payload".equals(normalized)
                || "metadata_json".equals(normalized);
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }
}
