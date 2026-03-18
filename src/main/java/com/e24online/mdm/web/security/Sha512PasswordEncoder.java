package com.e24online.mdm.web.security;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

public class Sha512PasswordEncoder implements PasswordEncoder {

    private final BCryptPasswordEncoder bcryptEncoder;

    public Sha512PasswordEncoder() {
        this(12);
    }

    public Sha512PasswordEncoder(int bcryptStrength) {
        int safeStrength = Math.max(10, Math.min(bcryptStrength, 14));
        this.bcryptEncoder = new BCryptPasswordEncoder(safeStrength);
    }

    @Override
    public String encode(CharSequence rawPassword) {
        if (rawPassword == null) {
            throw new IllegalArgumentException("rawPassword must not be null");
        }
        return bcryptEncoder.encode(rawPassword);
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        if (encodedPassword == null || encodedPassword.isBlank()) {
            return false;
        }
        if (isBcryptHash(encodedPassword)) {
            return bcryptEncoder.matches(rawPassword, encodedPassword);
        }
        if (!isLegacySha512Hex(encodedPassword)) {
            return false;
        }
        String expectedHash = sha512Hex(rawPassword);
        return MessageDigest.isEqual(
                expectedHash.getBytes(StandardCharsets.UTF_8),
                encodedPassword.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8)
        );
    }

    @Override
    public boolean upgradeEncoding(String encodedPassword) {
        if (encodedPassword == null || encodedPassword.isBlank()) {
            return true;
        }
        if (isLegacySha512Hex(encodedPassword)) {
            return true;
        }
        if (isBcryptHash(encodedPassword)) {
            return bcryptEncoder.upgradeEncoding(encodedPassword);
        }
        return true;
    }

    private String sha512Hex(CharSequence rawPassword) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-512");
            byte[] bytes = digest.digest(rawPassword.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-512 algorithm is not available", ex);
        }
    }

    private boolean isLegacySha512Hex(String encodedPassword) {
        String value = encodedPassword.trim();
        if (value.length() != 128) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean isHex = (c >= '0' && c <= '9')
                    || (c >= 'a' && c <= 'f')
                    || (c >= 'A' && c <= 'F');
            if (!isHex) {
                return false;
            }
        }
        return true;
    }

    private boolean isBcryptHash(String encodedPassword) {
        return encodedPassword.startsWith("$2a$")
                || encodedPassword.startsWith("$2b$")
                || encodedPassword.startsWith("$2y$");
    }
}
