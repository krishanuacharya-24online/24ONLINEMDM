package com.e24online.mdm.constants;

import org.jspecify.annotations.Nullable;

import java.util.regex.Pattern;

public class DeviceEnrollmentServiceConstants {

    private DeviceEnrollmentServiceConstants() {
        /* This utility class should not be instantiated */
    }

    public static final String OWNER_USER_ID = "owner_user_id";
    public static final String ENROLLMENT = "ENROLLMENT";
    public static final String ENROLLMENT_NO = "enrollmentNo";
    public static final String DEVICE_ENROLLMENT = "DEVICE_ENROLLMENT";
    public static final String SUCCESS = "SUCCESS";
    public static final String ACTIVE = "ACTIVE";
    public static final String EXPIRED = "EXPIRED";
    public static final String DE_ENROLLED = "DE_ENROLLED";
    public static final String AGENT_ENROLL = "agent-enroll";
    public static final int DEFAULT_SETUP_KEY_MAX_USES = 5;
    public static final int DEFAULT_SETUP_KEY_TTL_MINUTES = 60;
    public static final int SETUP_CODE_RAW_LENGTH = 12;
    public static final int MAX_PAGE_SIZE = 500;
    public static final int DEFAULT_PAGE_SIZE = 50;
    public static final int ENROLLMENT_NO_SUFFIX_LENGTH = 10;
    public static final String ALL_CHAR_NUM = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    public static final Pattern COMPACT_SETUP_CODE = Pattern.compile("^[A-Za-z0-9]{12}$");
    public static final Pattern GROUPED_SETUP_CODE = Pattern.compile("^[A-Za-z0-9]{3}(?:-[A-Za-z0-9]{3}){3}$");
    public static final String DEVICE_ENROLLMENTS_VIEWED = "DEVICE_ENROLLMENTS_VIEWED";
    public static final @Nullable String ENROLLMENT_NOT_FOUND = "Enrollment not found";
}
