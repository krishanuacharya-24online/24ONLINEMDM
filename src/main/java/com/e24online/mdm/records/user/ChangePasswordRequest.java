package com.e24online.mdm.records.user;

public record ChangePasswordRequest(String currentPassword, String newPassword, String confirmPassword) {
}
