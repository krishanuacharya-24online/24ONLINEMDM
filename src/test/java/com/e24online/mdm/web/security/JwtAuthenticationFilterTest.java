package com.e24online.mdm.web.security;

import com.e24online.mdm.config.ApiVersionConfig;
import com.e24online.mdm.domain.AuthUser;
import com.e24online.mdm.records.user.UserPrincipal;
import com.e24online.mdm.repository.AuthUserRepository;
import com.e24online.mdm.repository.TenantRepository;
import com.e24online.mdm.service.AuditEventService;
import com.e24online.mdm.service.BlockingDb;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpCookie;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.scheduler.Schedulers;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtService jwtService;

    @Mock
    private AuthUserRepository userRepository;

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private AuditEventService auditEventService;

    @Mock
    private Claims claims;

    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(
                new ApiVersionConfig("v1"),
                jwtService,
                userRepository,
                tenantRepository,
                auditEventService,
                new BlockingDb(Schedulers.immediate())
        );
        lenient().when(tenantRepository.findById(anyLong())).thenReturn(Optional.empty());
    }

    @Test
    void agentPathBypassesJwtCookieAuthentication() {
        ServerWebExchange exchange = exchange("/v1/agent/ingest", "token");
        AtomicReference<Authentication> authRef = new AtomicReference<>();

        filter.filter(exchange, capturingChain(authRef)).block();

        assertNull(authRef.get());
        verify(jwtService, never()).parseToken(anyString());
        verify(userRepository, never()).findActiveById(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void missingCookieSkipsAuthentication() {
        ServerWebExchange exchange = exchange("/v1/ui/devices", null);
        AtomicReference<Authentication> authRef = new AtomicReference<>();

        filter.filter(exchange, capturingChain(authRef)).block();

        assertNull(authRef.get());
        verify(jwtService, never()).parseToken(anyString());
    }

    @Test
    void validJwtCreatesAuthenticatedPrincipal() {
        ServerWebExchange exchange = exchange("/v1/ui/devices", "valid-token");
        AtomicReference<Authentication> authRef = new AtomicReference<>();

        when(jwtService.parseToken("valid-token")).thenReturn(claims);
        when(claims.getSubject()).thenReturn("alice");
        when(claims.get("role", String.class)).thenReturn("PRODUCT_ADMIN");
        when(claims.get("uid", Long.class)).thenReturn(11L);
        when(claims.get("tenantId", Long.class)).thenReturn(3L);
        when(claims.get("tokenVersion", Long.class)).thenReturn(2L);

        AuthUser user = new AuthUser();
        user.setId(11L);
        user.setUsername("alice");
        user.setStatus("ACTIVE");
        user.setDeleted(false);
        user.setTokenVersion(2L);
        when(userRepository.findActiveById(11L)).thenReturn(Optional.of(user));

        filter.filter(exchange, capturingChain(authRef)).block();

        Authentication auth = authRef.get();
        assertNotNull(auth);
        assertTrue(auth.isAuthenticated());
        UserPrincipal principal = (UserPrincipal) auth.getPrincipal();
        assertEquals(11L, principal.id());
        assertEquals("alice", principal.username());
        assertEquals("PRODUCT_ADMIN", principal.role());
        assertEquals(3L, principal.tenantId());
        assertEquals("ROLE_PRODUCT_ADMIN", auth.getAuthorities().iterator().next().getAuthority());
    }

    @Test
    void tokenVersionMismatchSkipsAuthentication() {
        ServerWebExchange exchange = exchange("/v1/ui/devices", "version-mismatch");
        AtomicReference<Authentication> authRef = new AtomicReference<>();

        when(jwtService.parseToken("version-mismatch")).thenReturn(claims);
        when(claims.getSubject()).thenReturn("bob");
        when(claims.get("role", String.class)).thenReturn("TENANT_USER");
        when(claims.get("uid", Long.class)).thenReturn(21L);
        when(claims.get("tenantId", Long.class)).thenReturn(8L);
        when(claims.get("tokenVersion", Long.class)).thenReturn(1L);

        AuthUser user = new AuthUser();
        user.setId(21L);
        user.setStatus("ACTIVE");
        user.setDeleted(false);
        user.setTokenVersion(2L);
        when(userRepository.findActiveById(21L)).thenReturn(Optional.of(user));

        filter.filter(exchange, capturingChain(authRef)).block();

        assertNull(authRef.get());
    }

    @Test
    void invalidJwtExceptionSkipsAuthentication() {
        ServerWebExchange exchange = exchange("/v1/ui/devices", "bad-token");
        AtomicReference<Authentication> authRef = new AtomicReference<>();

        when(jwtService.parseToken("bad-token")).thenThrow(new JwtException("bad token"));

        filter.filter(exchange, capturingChain(authRef)).block();

        assertNull(authRef.get());
    }

    private ServerWebExchange exchange(String path, String cookieValue) {
        MockServerHttpRequest.BaseBuilder<?> builder = MockServerHttpRequest.get(path);
        if (cookieValue != null) {
            builder.cookie(new HttpCookie("ACCESS_TOKEN", cookieValue));
        }
        return MockServerWebExchange.from(builder.build());
    }

    private WebFilterChain capturingChain(AtomicReference<Authentication> authRef) {
        return exchange -> ReactiveSecurityContextHolder.getContext()
                .map(context -> context.getAuthentication())
                .doOnNext(authRef::set)
                .then();
    }
}
