package com.e24online.mdm.utils;

/**
 * Result of breach check with reason code.
 */
public class BreachCheckResult {
    private final boolean breached;
    private final String reasonCode;

    public BreachCheckResult(boolean breached, String reasonCode) {
        this.breached = breached;
        this.reasonCode = reasonCode;
    }

    public boolean isBreached() {
        return breached;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public String getHumanReadableReason() {
        return switch (reasonCode) {
            case "EMPTY_PASSWORD" -> "Password cannot be empty";
            case "FOUND_IN_LOCAL_DATABASE" -> "Password found in known breach database";
            case "FOUND_IN_LOCAL_DATABASE_HASH" -> "Password hash found in known breach database";
            case "KEYBOARD_PATTERN_DETECTED" -> "Password contains keyboard pattern (e.g., qwerty)";
            case "SEQUENCE_PATTERN_DETECTED" -> "Password contains sequential pattern (e.g., 123, abc)";
            case "REPEAT_PATTERN_DETECTED" -> "Password contains repeated characters (e.g., aaa)";
            case "DATE_PATTERN_DETECTED" -> "Password contains date pattern (YYYYMMDD)";
            case "COMMON_PASSWORD" -> "Password is too common";
            case "LEET_SPEAK_COMMON_PASSWORD" -> "Password is a variation of a common password";
            case "FOUND_IN_HIBP_DATABASE" -> "Password found in Have I Been Pwned database";
            case "PASSWORD_ACCEPTABLE" -> "Password passed all checks";
            default -> "Password failed security check";
        };
    }
}