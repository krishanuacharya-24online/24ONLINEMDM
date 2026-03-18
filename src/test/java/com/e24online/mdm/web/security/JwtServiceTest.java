package com.e24online.mdm.web.security;

import com.e24online.mdm.domain.AuthUser;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    @Test
    void createsAndParsesAccessToken() {
        JwtService jwtService = new JwtService(
                "01234567890123456789012345678901",
                900,
                604800
        );
        AuthUser user = new AuthUser();
        user.setId(7L);
        user.setUsername("admin");
        user.setRole("PRODUCT_ADMIN");
        user.setTenantId(null);

        String token = jwtService.generateAccessToken(user);
        Claims claims = jwtService.parseToken(token);

        assertEquals("admin", claims.getSubject());
        assertEquals(7L, claims.get("uid", Long.class));
        assertEquals("PRODUCT_ADMIN", claims.get("role", String.class));
        assertNotNull(claims.getIssuedAt());
        assertNotNull(claims.getExpiration());
    }

    @Test
    void rejectsTooShortSecret() {
        assertThrows(IllegalStateException.class, () -> new JwtService("short-secret", 900, 604800));
    }
}
