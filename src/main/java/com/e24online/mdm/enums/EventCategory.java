package com.e24online.mdm.enums;

public enum EventCategory {
    SCORE,        // Trust score changes
    SECURITY,     // Security events (root, emulator, etc.)
    APPLICATION,  // App-related events
    LIFECYCLE,    // OS lifecycle events
    DECISION,     // Decision changes
    REMEDIATION,  // Remediation events
    SYSTEM        // System events
}
