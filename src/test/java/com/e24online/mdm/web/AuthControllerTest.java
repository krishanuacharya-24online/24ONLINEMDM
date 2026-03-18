package com.e24online.mdm.web;

import com.e24online.mdm.domain.AuthRefreshToken;
import com.e24online.mdm.domain.AuthUser;
import com.e24online.mdm.domain.Tenant;
import com.e24online.mdm.records.user.ChangePasswordRequest;
import com.e24online.mdm.records.user.LoginRequest;
import com.e24online.mdm.records.user.LoginResponse;
import com.e24online.mdm.records.user.UserPrincipal;
import com.e24online.mdm.repository.AuthRefreshTokenRepository;
import com.e24online.mdm.repository.AuthUserRepository;
import com.e24online.mdm.repository.TenantRepository;
import com.e24online.mdm.service.AuditEventService;
import com.e24online.mdm.service.BlockingDb;
import com.e24online.mdm.service.LocalBreachedPasswordService;
import com.e24online.mdm.web.security.JwtService;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.server.csrf.CsrfToken;
import org.springframework.security.web.server.csrf.ServerCsrfTokenRepository;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthUserRepository userRepository;

    @Mock
    private AuthRefreshTokenRepository refreshRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private AuditEventService auditEventService;

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private ServerCsrfTokenRepository csrfTokenRepository;

    @Mock
    private LocalBreachedPasswordService localBreachedPasswordService;

    private AuthController controller;

    @BeforeEach
    void setUp() {
        controller = new AuthController(
                userRepository,
                refreshRepository,
                passwordEncoder,
                jwtService,
                new BlockingDb(Schedulers.immediate()),
                auditEventService,
                tenantRepository,
                csrfTokenRepository,
                localBreachedPasswordService
        );
        lenient().when(jwtService.accessTtl()).thenReturn(Duration.ofMinutes(15));
        lenient().when(jwtService.refreshTtl()).thenReturn(Duration.ofDays(7));
        lenient().when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant(1L, "tenant-a")));
        lenient().when(localBreachedPasswordService.isBreached(any())).thenReturn(false);
    }

    @Test
    void login_returnsBadRequestWhenRequestInvalid() {
        MockServerWebExchange exchange = exchange("/auth/login");

        ResponseEntity<LoginResponse> response = controller
                .login(new LoginRequest(" ", " "), exchange)
                .block();

        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void login_returnsUnauthorizedWhenUserMissing() {
        MockServerWebExchange exchange = exchange("/auth/login");
        when(userRepository.findByUsernameAndIsDeletedFalse("alice")).thenReturn(Optional.empty());

        ResponseEntity<LoginResponse> response = controller
                .login(new LoginRequest("alice", "StrongPass1!"), exchange)
                .block();

        assertNotNull(response);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    void login_successReusesExistingRefreshToken() {
        MockServerWebExchange exchange = exchange("/auth/login");
        AuthUser user = activeUser(11L, "alice", "hash");
        when(userRepository.findByUsernameAndIsDeletedFalse("alice")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("StrongPass1!", "hash")).thenReturn(true);
        when(passwordEncoder.upgradeEncoding("hash")).thenReturn(false);
        when(jwtService.generateAccessToken(user)).thenReturn("access-token");
        when(jwtService.generateRefreshToken(user, "jti-existing")).thenReturn("refresh-token");
        when(refreshRepository.findByUserId(11L)).thenReturn(List.of(activeRefreshToken(11L, "jti-existing")));

        ResponseEntity<LoginResponse> response = controller
                .login(new LoginRequest("alice", "StrongPass1!"), exchange)
                .block();

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("alice", response.getBody().username());
        assertNotNull(exchange.getResponse().getCookies().getFirst("ACCESS_TOKEN"));
        assertNotNull(exchange.getResponse().getCookies().getFirst("REFRESH_TOKEN"));
        verify(refreshRepository, never()).save(any(AuthRefreshToken.class));
    }

    @Test
    void login_successCreatesRefreshTokenAndUpgradesPasswordHash() {
        MockServerWebExchange exchange = exchange("/auth/login");
        AuthUser user = activeUser(22L, "bob", "old-hash");
        when(userRepository.findByUsernameAndIsDeletedFalse("bob")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("StrongPass1!", "old-hash")).thenReturn(true);
        when(passwordEncoder.upgradeEncoding("old-hash")).thenReturn(true);
        when(passwordEncoder.encode("StrongPass1!")).thenReturn("new-hash");
        when(refreshRepository.findByUserId(22L)).thenReturn(List.of());
        when(jwtService.generateAccessToken(user)).thenReturn("access-token");
        when(jwtService.newJti()).thenReturn("jti-new");
        when(jwtService.generateRefreshToken(user, "jti-new")).thenReturn("refresh-token");

        ResponseEntity<LoginResponse> response = controller
                .login(new LoginRequest("bob", "StrongPass1!"), exchange)
                .block();

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("new-hash", user.getPasswordHash());
        verify(userRepository, times(1)).save(user);

        ArgumentCaptor<AuthRefreshToken> tokenCaptor = ArgumentCaptor.forClass(AuthRefreshToken.class);
        verify(refreshRepository, times(1)).save(tokenCaptor.capture());
        assertEquals("jti-new", tokenCaptor.getValue().getJti());
        assertFalse(tokenCaptor.getValue().isRevoked());
    }

    @Test
    void refresh_returnsUnauthorizedWhenCookieMissing() {
        MockServerWebExchange exchange = exchange("/auth/refresh");

        ResponseEntity<Void> response = controller.refresh(exchange).block();

        assertNotNull(response);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    void refresh_successKeepsExistingRefreshToken() {
        MockServerWebExchange exchange = exchangeWithRefreshToken("old-refresh");
        Claims claims = org.mockito.Mockito.mock(Claims.class);
        AuthUser user = activeUser(33L, "carol", "hash");
        AuthRefreshToken stored = activeRefreshToken(33L, "jti-old");
        stored.setRevoked(false);
        stored.setExpiresAt(OffsetDateTime.now().plusMinutes(30));

        when(jwtService.parseToken("old-refresh")).thenReturn(claims);
        when(claims.getId()).thenReturn("jti-old");
        when(claims.get("uid", Long.class)).thenReturn(33L);
        when(refreshRepository.findByJti("jti-old")).thenReturn(Optional.of(stored));
        when(userRepository.findById(33L)).thenReturn(Optional.of(user));
        when(jwtService.generateAccessToken(user)).thenReturn("new-access");

        ResponseEntity<Void> response = controller.refresh(exchange).block();

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertFalse(stored.isRevoked());
        assertNotNull(exchange.getResponse().getCookies().getFirst("ACCESS_TOKEN"));
        assertTrue(exchange.getResponse().getCookies().getFirst("REFRESH_TOKEN") == null);
        verify(refreshRepository, never()).save(any(AuthRefreshToken.class));
    }

    @Test
    void logout_clearsCookies() {
        MockServerWebExchange exchange = exchange("/auth/logout");

        ResponseEntity<Void> response = controller.logout(null, exchange).block();

        assertNotNull(response);
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        ResponseCookie access = exchange.getResponse().getCookies().getFirst("ACCESS_TOKEN");
        ResponseCookie refresh = exchange.getResponse().getCookies().getFirst("REFRESH_TOKEN");
        assertNotNull(access);
        assertNotNull(refresh);
        assertEquals(0, access.getMaxAge().getSeconds());
        assertEquals(0, refresh.getMaxAge().getSeconds());
    }

    @Test
    void me_returnsAuthenticatedUserDetails() {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                new UserPrincipal(7L, "dave", "TENANT_ADMIN", 99L),
                null
        );

        Map<String, Object> body = controller.me(auth).block();

        assertNotNull(body);
        assertEquals(true, body.get("authenticated"));
        assertEquals("dave", body.get("username"));
        assertEquals("TENANT_ADMIN", body.get("role"));
    }

    @Test
    void csrf_returnsGeneratedToken() {
        MockServerWebExchange exchange = exchange("/auth/csrf");
        CsrfToken token = org.mockito.Mockito.mock(CsrfToken.class);
        when(token.getToken()).thenReturn("csrf-token");
        when(csrfTokenRepository.generateToken(exchange)).thenReturn(Mono.just(token));
        when(csrfTokenRepository.saveToken(exchange, token)).thenReturn(Mono.empty());

        Map<String, String> body = controller.csrf(exchange).block();

        assertNotNull(body);
        assertEquals("csrf-token", body.get("token"));
    }

    @Test
    void changePassword_returnsUnauthorizedWhenAuthenticationMissing() {
        MockServerWebExchange exchange = exchange("/auth/change-password");
        ChangePasswordRequest request =
                new ChangePasswordRequest("OldPass1!", "NewStrongPass1!", "NewStrongPass1!");

        ResponseEntity<Map<String, String>> response = controller.changePassword(request, null, exchange).block();

        assertNotNull(response);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    void changePassword_returnsBadRequestForWeakPassword() {
        MockServerWebExchange exchange = exchange("/auth/change-password");
        Authentication auth = new UsernamePasswordAuthenticationToken(
                new UserPrincipal(41L, "erin", "TENANT_USER", 1L),
                null
        );
        ChangePasswordRequest request =
                new ChangePasswordRequest("OldPass1!", "weak", "weak");

        ResponseEntity<Map<String, String>> response = controller.changePassword(request, auth, exchange).block();

        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void changePassword_successUpdatesPasswordRevokesTokensAndClearsCookies() {
        MockServerWebExchange exchange = exchange("/auth/change-password");
        Authentication auth = new UsernamePasswordAuthenticationToken(
                new UserPrincipal(52L, "frank", "TENANT_USER", 1L),
                null
        );
        ChangePasswordRequest request =
                new ChangePasswordRequest("OldPass1!", "NewStrongPass1!", "NewStrongPass1!");

        AuthUser user = activeUser(52L, "frank", "old-hash");
        user.setTokenVersion(3L);
        when(userRepository.findById(52L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("OldPass1!", "old-hash")).thenReturn(true);
        when(passwordEncoder.encode("NewStrongPass1!")).thenReturn("new-hash");
        AuthRefreshToken activeToken = activeRefreshToken(52L, "jti-a");
        AuthRefreshToken revokedToken = activeRefreshToken(52L, "jti-r");
        revokedToken.setRevoked(true);
        when(refreshRepository.findByUserId(52L)).thenReturn(List.of(activeToken, revokedToken));

        ResponseEntity<Map<String, String>> response = controller.changePassword(request, auth, exchange).block();

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("new-hash", user.getPasswordHash());
        assertEquals(4L, user.getTokenVersion());
        verify(refreshRepository, times(1)).save(eq(activeToken));
        verify(refreshRepository, times(0)).save(eq(revokedToken));
        assertEquals(0, exchange.getResponse().getCookies().getFirst("ACCESS_TOKEN").getMaxAge().getSeconds());
    }

    private MockServerWebExchange exchange(String path) {
        return MockServerWebExchange.from(MockServerHttpRequest.post(path).build());
    }

    private MockServerWebExchange exchangeWithRefreshToken(String token) {
        return MockServerWebExchange.from(
                MockServerHttpRequest.post("/auth/refresh")
                        .cookie(new org.springframework.http.HttpCookie("REFRESH_TOKEN", token))
                        .build()
        );
    }

    private AuthUser activeUser(Long id, String username, String hash) {
        AuthUser user = new AuthUser();
        user.setId(id);
        user.setUsername(username);
        user.setPasswordHash(hash);
        user.setRole("TENANT_USER");
        user.setTenantId(1L);
        user.setStatus("ACTIVE");
        user.setDeleted(false);
        return user;
    }

    private AuthRefreshToken activeRefreshToken(Long userId, String jti) {
        AuthRefreshToken token = new AuthRefreshToken();
        token.setUserId(userId);
        token.setJti(jti);
        token.setRevoked(false);
        token.setExpiresAt(OffsetDateTime.now().plusHours(1));
        token.setCreatedAt(OffsetDateTime.now().minusMinutes(5));
        return token;
    }

    private Tenant tenant(Long id, String tenantId) {
        Tenant tenant = new Tenant();
        tenant.setId(id);
        tenant.setTenantId(tenantId);
        tenant.setStatus("ACTIVE");
        tenant.setDeleted(false);
        return tenant;
    }
}
