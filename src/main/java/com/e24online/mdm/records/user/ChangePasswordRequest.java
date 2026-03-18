package com.e24online.mdm.records;

public record ChangePasswordRequest(String currentPassword, String newPassword, String confirmPassword) {
}
