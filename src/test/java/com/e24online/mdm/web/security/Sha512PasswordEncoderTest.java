package com.e24online.mdm.web.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;

class Sha512PasswordEncoderTest {

    private final Sha512PasswordEncoder encoder = new Sha512PasswordEncoder();

    @Test
    void encodesUsingSha512Hex() {
        String expectedSha512 = "c7ad44cbad762a5da0a452f9e854fdc1e0e7a52a38015f23f3eab1d80b931dd472634dfac71cd34ebc35d16ab7fb8a90c81f975113d6c7538dc69dd8de9077ec";
        assertTrue(encoder.matches("admin", expectedSha512));
    }

    @Test
    void matchesSha512Hashes() {
        String encoded = encoder.encode("admin");
        assertTrue(encoder.matches("admin", encoded));
        assertFalse(encoder.matches("wrong", encoded));
    }

    @Test
    void supportsLegacyBcryptAndMarksForUpgrade() {
        String bcrypt = new BCryptPasswordEncoder().encode("admin");
        assertTrue(encoder.matches("admin", bcrypt));
        assertTrue(encoder.upgradeEncoding(bcrypt));
    }

    @Test
    void rejectsNullRawPassword() {
        assertThrows(IllegalArgumentException.class, () -> encoder.encode(null));
    }
}
