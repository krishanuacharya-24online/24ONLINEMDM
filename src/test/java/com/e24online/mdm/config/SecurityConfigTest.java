package com.e24online.mdm.config;

import com.e24online.mdm.web.security.JwtAuthenticationFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.csrf.CookieServerCsrfTokenRepository;
import org.springframework.security.web.server.csrf.CsrfToken;
import org.springframework.security.web.server.csrf.ServerCsrfTokenRepository;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class SecurityConfigTest {

    private SecurityConfig securityConfig;

    @BeforeEach
    void setUp() {
        securityConfig = new SecurityConfig(new ApiVersionConfig("v1"));
    }

    @Test
    void passwordEncoderBean_hashesAndMatchesPassword() {
        PasswordEncoder encoder = securityConfig.passwordEncoder(10);

        String raw = "StrongPass1!";
        String encoded = encoder.encode(raw);

        assertNotNull(encoded);
        assertNotEquals(raw, encoded);
        assertTrue(encoder.matches(raw, encoded));
    }

    @Test
    void csrfTokenRepositoryBean_isCookieRepository() {
        ServerCsrfTokenRepository repo = securityConfig.csrfTokenRepository();
        assertTrue(repo instanceof CookieServerCsrfTokenRepository);
    }

    @Test
    void springSecurityFilterChain_buildsSuccessfully() {
        JwtAuthenticationFilter jwtFilter = mock(JwtAuthenticationFilter.class);
        ServerHttpSecurity http = ServerHttpSecurity.http();

        SecurityWebFilterChain chain = securityConfig.springSecurityFilterChain(
                http,
                jwtFilter,
                securityConfig.csrfTokenRepository()
        );

        assertNotNull(chain);
    }

    @Test
    void authenticationEntryPoint_returnsUnauthorizedForApiAndManagementPaths() {
        ServerAuthenticationEntryPoint entryPoint = authenticationEntryPointFromChain();

        List<String> paths = List.of(
                "/v1/devices",
                "/auth/login",
                "/actuator/health",
                "/v3/api-docs",
                "/swagger-ui/index.html"
        );

        for (String path : paths) {
            MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get(path).build());
            entryPoint.commence(exchange, new org.springframework.security.authentication.AuthenticationCredentialsNotFoundException("missing")).block();
            assertEquals(org.springframework.http.HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        }
    }

    @Test
    void authenticationEntryPoint_redirectsNonApiPathToLogin() {
        ServerAuthenticationEntryPoint entryPoint = authenticationEntryPointFromChain();
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/dashboard").build());

        entryPoint.commence(exchange, new org.springframework.security.authentication.AuthenticationCredentialsNotFoundException("missing")).block();

        assertEquals(org.springframework.http.HttpStatus.TEMPORARY_REDIRECT, exchange.getResponse().getStatusCode());
        assertEquals("/login", exchange.getResponse().getHeaders().getLocation().toString());
    }

    @Test
    void csrfCookieWebFilter_withoutCsrfAttribute_passesThrough() {
        WebFilter filter = securityConfig.csrfCookieWebFilter();
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api").build());

        AtomicBoolean chainCalled = new AtomicBoolean(false);
        WebFilterChain chain = webExchange -> {
            chainCalled.set(true);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertTrue(chainCalled.get());
    }

    @Test
    void csrfCookieWebFilter_withCsrfAttribute_subscribesAndPassesThrough() {
        WebFilter filter = securityConfig.csrfCookieWebFilter();
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api").build());
        CsrfToken csrfToken = simpleToken("X-CSRF-TOKEN", "_csrf", "token-value");
        exchange.getAttributes().put(CsrfToken.class.getName(), Mono.just(csrfToken));

        AtomicBoolean chainCalled = new AtomicBoolean(false);
        WebFilterChain chain = webExchange -> {
            chainCalled.set(true);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertTrue(chainCalled.get());
    }

    @Test
    void securityHeadersWebFilter_setsHeadersAndPreservesExistingValues() {
        WebFilter filter = securityConfig.securityHeadersWebFilter();
        MockServerHttpRequest request = MockServerHttpRequest.get("/ui").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getResponse().getHeaders().set("X-Frame-Options", "SAMEORIGIN");

        WebFilterChain chain = webExchange -> Mono.empty();
        filter.filter(exchange, chain).block();

        assertEquals("SAMEORIGIN", exchange.getResponse().getHeaders().getFirst("X-Frame-Options"));
        assertEquals("nosniff", exchange.getResponse().getHeaders().getFirst("X-Content-Type-Options"));
        assertNotNull(exchange.getResponse().getHeaders().getFirst("Content-Security-Policy"));
        assertEquals("strict-origin-when-cross-origin", exchange.getResponse().getHeaders().getFirst("Referrer-Policy"));
    }

    private CsrfToken simpleToken(String headerName, String parameterName, String token) {
        return new CsrfToken() {
            @Override
            public String getHeaderName() {
                return headerName;
            }

            @Override
            public String getParameterName() {
                return parameterName;
            }

            @Override
            public String getToken() {
                return token;
            }
        };
    }

    private ServerAuthenticationEntryPoint authenticationEntryPointFromChain() {
        JwtAuthenticationFilter jwtFilter = mock(JwtAuthenticationFilter.class);
        SecurityWebFilterChain chain = securityConfig.springSecurityFilterChain(
                ServerHttpSecurity.http(),
                jwtFilter,
                securityConfig.csrfTokenRepository()
        );

        List<?> filters = chain.getWebFilters().collectList().block();
        assertNotNull(filters);
        Object exceptionTranslationFilter = filters.stream()
                .filter(f -> f.getClass().getName().contains("ExceptionTranslationWebFilter"))
                .findFirst()
                .orElseThrow();
        Object entryPoint = ReflectionTestUtils.getField(exceptionTranslationFilter, "authenticationEntryPoint");
        assertTrue(entryPoint instanceof ServerAuthenticationEntryPoint);
        return (ServerAuthenticationEntryPoint) entryPoint;
    }
}
