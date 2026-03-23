package com.e24online.mdm.enums;

public enum OSType {
    ANDROID("Android"),
    IOS("iOS"),
    WINDOWS("Windows"),
    MACOS("macOS"),
    LINUX("Linux"),
    CHROMEOS("ChromeOS"),
    FREEBSD("FreeBSD"),
    OPENBSD("OpenBSD");

    private final String displayName;

    OSType(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public static String display(String osType) {
        if (osType == null) return "device";
        try {
            return valueOf(osType.toUpperCase()).displayName;
        } catch (IllegalArgumentException _) {
            return osType;
        }
    }
}