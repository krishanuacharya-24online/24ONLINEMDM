package com.e24online.mdm.service;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentWorkflowValueUtilsTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void safeTextAndTrimHelpersWorkAsExpected() {
        assertEquals("", AgentWorkflowValueUtils.safeText(null));
        assertEquals("hello", AgentWorkflowValueUtils.safeText("  hello  "));
        assertNull(AgentWorkflowValueUtils.trimToNull("  "));
        assertEquals("abc", AgentWorkflowValueUtils.trimToNull(" abc "));
        assertEquals("AB", AgentWorkflowValueUtils.normalizeUpper(" ab "));
        assertNull(AgentWorkflowValueUtils.normalizeUpper("   "));
    }

    @Test
    void truncateAndCapBehaviorsAreCorrect() {
        assertNull(AgentWorkflowValueUtils.truncate(null, 5));
        assertEquals("", AgentWorkflowValueUtils.truncate("value", 0));
        assertEquals("val", AgentWorkflowValueUtils.truncate("value", 3));
        assertEquals("abc", AgentWorkflowValueUtils.trimToNullAndCap(" abc ", 10));
        assertEquals("ab", AgentWorkflowValueUtils.trimToNullAndCap(" abc ", 2));
        assertEquals("", AgentWorkflowValueUtils.trimToNullAndCap(" abc ", 0));
    }

    @Test
    void equalityAndFirstNonBlankBehaviorsAreCorrect() {
        assertTrue(AgentWorkflowValueUtils.equalsIgnoreCase(null, null));
        assertFalse(AgentWorkflowValueUtils.equalsIgnoreCase(null, "x"));
        assertTrue(AgentWorkflowValueUtils.equalsIgnoreCase("Admin", "ADMIN"));
        assertNull(AgentWorkflowValueUtils.firstNonBlank((String[]) null));
        assertEquals("x", AgentWorkflowValueUtils.firstNonBlank(" ", " x ", "y"));
    }

    @Test
    void jsonTextNumberBooleanConversionWorks() {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("name", "  device-a ");
        node.put("intRaw", 42);
        node.put("intText", "101");
        node.put("boolRaw", true);
        node.put("boolText", "false");
        node.put("invalidTextInt", "12x");
        node.put("invalidBool", "maybe");

        assertEquals("device-a", AgentWorkflowValueUtils.text(node, "name"));
        assertNull(AgentWorkflowValueUtils.text(node, "missing"));
        assertEquals(42, AgentWorkflowValueUtils.intValue(node.get("intRaw")));
        assertEquals(101, AgentWorkflowValueUtils.intValue(node.get("intText")));
        assertNull(AgentWorkflowValueUtils.intValue(node.get("invalidTextInt")));
        assertTrue(AgentWorkflowValueUtils.boolValue(node.get("boolRaw")));
        assertFalse(AgentWorkflowValueUtils.boolValue(node.get("boolText")));
        assertNull(AgentWorkflowValueUtils.boolValue(node.get("invalidBool")));
        assertNull(AgentWorkflowValueUtils.boolValue(null));
    }

    @Test
    void parseAndStringifyHelpersWork() {
        assertEquals(5, AgentWorkflowValueUtils.parseIntOrNull(" 5 "));
        assertNull(AgentWorkflowValueUtils.parseIntOrNull("x5"));
        assertEquals(2.5, AgentWorkflowValueUtils.toDouble("2.5"));
        assertEquals(2.0, AgentWorkflowValueUtils.toDouble(2));
        assertNull(AgentWorkflowValueUtils.toDouble(new Object()));

        ObjectNode obj = objectMapper.createObjectNode().put("a", 1);
        assertEquals("abc", AgentWorkflowValueUtils.stringifyNode(objectMapper.getNodeFactory().textNode(" abc ")));
        assertEquals("{\"a\":1}", AgentWorkflowValueUtils.stringifyNode(obj));
        assertNull(AgentWorkflowValueUtils.stringifyNode(null));
    }

    @Test
    void expectedCollectionAndSafeShortWork() {
        List<String> list = List.of(" allow ", "block");
        assertEquals(List.of("ALLOW", "BLOCK"), AgentWorkflowValueUtils.expectedCollection(list));
        assertEquals(List.of("NOTIFY"), AgentWorkflowValueUtils.expectedCollection("notify"));
        assertEquals((short) 7, AgentWorkflowValueUtils.safeShort((short) 7, (short) 1));
        assertEquals((short) 1, AgentWorkflowValueUtils.safeShort(null, (short) 1));
    }
}
