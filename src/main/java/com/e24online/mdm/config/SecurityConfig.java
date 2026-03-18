package com.e24online.mdm.config;

import com.e24online.mdm.constants.SecurityConfigConstants;
import com.e24online.mdm.web.security.JwtAuthenticationFilter;
import com.e24online.mdm.web.security.Sha512PasswordEncoder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.csrf.CookieServerCsrfTokenRepository;
import org.springframework.security.web.server.csrf.CsrfToken;
import org.springframework.security.web.server.csrf.ServerCsrfTokenRepository;
import org.springframework.security.web.server.csrf.ServerCsrfTokenRequestAttributeHandler;
import org.springframework.security.web.server.util.matcher.OrServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;
import org.springframework.web.server.WebFilter;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
public class SecurityConfig {

    private final ApiVersionConfig apiVersionConfig;

    @Value("${reports.superset.enabled:false}")
    private boolean supersetReportsEnabled;

    @Value("${reports.superset.base-url:}")
    private String supersetBaseUrl;

    public SecurityConfig(ApiVersionConfig apiVersionConfig) {
        this.apiVersionConfig = apiVersionConfig;
    }

    @Bean
    public PasswordEncoder passwordEncoder(
            @Value("${security.password.bcrypt-strength:12}") int bcryptStrength
    ) {
        return new Sha512PasswordEncoder(bcryptStrength);
    }

    @Bean
    public ServerCsrfTokenRepository csrfTokenRepository() {
        return CookieServerCsrfTokenRepository.withHttpOnlyFalse();
    }

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http,
                                                            JwtAuthenticationFilter jwtFilter,
                                                            ServerCsrfTokenRepository csrfTokenRepository) {
        ServerCsrfTokenRequestAttributeHandler csrfRequestHandler = new ServerCsrfTokenRequestAttributeHandler();
        var csrfMatcher = new OrServerWebExchangeMatcher(
                ServerWebExchangeMatchers.pathMatchers(HttpMethod.POST,
                        "/auth/logout",
                        "/auth/change-password",
                        apiVersionConfig.path(SecurityConfigConstants.ADMIN_URL),
                        apiVersionConfig.path(SecurityConfigConstants.DEVICE_URL),
                        apiVersionConfig.path(SecurityConfigConstants.POLICY_URL),
                        apiVersionConfig.path(SecurityConfigConstants.OS_LIFECYCLE_URL),
                        apiVersionConfig.path(SecurityConfigConstants.CATALOG_URL),
                        apiVersionConfig.path(SecurityConfigConstants.EVALUATIONS_URL)
                ),
                ServerWebExchangeMatchers.pathMatchers(HttpMethod.PUT,
                        apiVersionConfig.path(SecurityConfigConstants.ADMIN_URL),
                        apiVersionConfig.path(SecurityConfigConstants.DEVICE_URL),
                        apiVersionConfig.path(SecurityConfigConstants.POLICY_URL),
                        apiVersionConfig.path(SecurityConfigConstants.OS_LIFECYCLE_URL),
                        apiVersionConfig.path(SecurityConfigConstants.CATALOG_URL),
                        apiVersionConfig.path(SecurityConfigConstants.EVALUATIONS_URL)
                ),
                ServerWebExchangeMatchers.pathMatchers(HttpMethod.PATCH,
                        apiVersionConfig.path(SecurityConfigConstants.ADMIN_URL),
                        apiVersionConfig.path(SecurityConfigConstants.DEVICE_URL),
                        apiVersionConfig.path(SecurityConfigConstants.POLICY_URL),
                        apiVersionConfig.path(SecurityConfigConstants.OS_LIFECYCLE_URL),
                        apiVersionConfig.path(SecurityConfigConstants.CATALOG_URL),
                        apiVersionConfig.path(SecurityConfigConstants.EVALUATIONS_URL)
                ),
                ServerWebExchangeMatchers.pathMatchers(HttpMethod.DELETE,
                        apiVersionConfig.path(SecurityConfigConstants.ADMIN_URL),
                        apiVersionConfig.path(SecurityConfigConstants.DEVICE_URL),
                        apiVersionConfig.path(SecurityConfigConstants.POLICY_URL),
                        apiVersionConfig.path(SecurityConfigConstants.OS_LIFECYCLE_URL),
                        apiVersionConfig.path(SecurityConfigConstants.CATALOG_URL),
                        apiVersionConfig.path(SecurityConfigConstants.EVALUATIONS_URL)
                )
        );

        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository)
                        .csrfTokenRequestHandler(csrfRequestHandler)
                        .requireCsrfProtectionMatcher(csrfMatcher)
                )
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable);

        http.exceptionHandling(ex -> ex.authenticationEntryPoint((exchange, e) -> {
            String path = exchange.getRequest().getPath().value();
            if (apiVersionConfig.isVersionedPath(path)
                    || path.startsWith("/auth/")
                    || path.startsWith("/actuator")
                    || path.startsWith("/v3/api-docs")
                    || path.startsWith("/swagger-ui")) {
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                return exchange.getResponse().setComplete();
            }
            exchange.getResponse().setStatusCode(HttpStatus.TEMPORARY_REDIRECT);
            exchange.getResponse().getHeaders().setLocation(URI.create("/login"));
            return exchange.getResponse().setComplete();
        }));

        http.addFilterAt(jwtFilter, SecurityWebFiltersOrder.AUTHENTICATION);

        http.authorizeExchange(exchanges -> exchanges
                .pathMatchers(
                        "/auth/login",
                        "/auth/refresh",
                        "/auth/logout",
                        "/auth/csrf",
                        "/",
                        "/login",
                        "/signin",
                        "/login.html",
                        "/error",
                        "/favicon.ico",
                        "/assets/**"
                ).permitAll()
                .pathMatchers(
                        apiVersionConfig.path("/agent/**")
                ).permitAll()
                .pathMatchers(
                        "/actuator/health/liveness",
                        "/actuator/health/readiness"
                ).permitAll()
                .pathMatchers(
                        "/actuator/**"
                ).hasRole("PRODUCT_ADMIN")
                .pathMatchers(
                        "/swagger-ui.html",
                        "/swagger-ui/**",
                        "/v3/api-docs/**"
                ).hasRole("PRODUCT_ADMIN")
                .pathMatchers(apiVersionConfig.path("/admin/tenants/**"))
                .hasRole("PRODUCT_ADMIN")
                .pathMatchers(apiVersionConfig.path("/admin/users/**"))
                .hasAnyRole("PRODUCT_ADMIN", "TENANT_ADMIN")
                .anyExchange().authenticated()
        );

        return http.build();
    }

    @Bean
    public WebFilter csrfCookieWebFilter() {
        return (exchange, chain) -> {
            Mono<CsrfToken> csrfToken = exchange.getAttribute(CsrfToken.class.getName());
            if (csrfToken == null) {
                return chain.filter(exchange);
            }
            return csrfToken.then(chain.filter(exchange));
        };
    }

    @Bean
    public WebFilter securityHeadersWebFilter() {
        return (exchange, chain) -> {
            var headers = exchange.getResponse().getHeaders();
            setHeaderIfAbsent(headers, "Content-Security-Policy",
                    buildContentSecurityPolicy());
            setHeaderIfAbsent(headers, "Referrer-Policy", "strict-origin-when-cross-origin");
            setHeaderIfAbsent(headers, "Permissions-Policy", "camera=(), microphone=(), geolocation=(), payment=(), usb=()");
            setHeaderIfAbsent(headers, "X-Content-Type-Options", "nosniff");
            setHeaderIfAbsent(headers, "X-Frame-Options", "DENY");
            setHeaderIfAbsent(headers, "Cross-Origin-Opener-Policy", "same-origin");
            setHeaderIfAbsent(headers, "Cross-Origin-Resource-Policy", "same-origin");
            return chain.filter(exchange);
        };
    }

    private static void setHeaderIfAbsent(HttpHeaders headers, String name, String value) {
        if (headers.getFirst(name) == null) {
            headers.set(name, value);
        }
    }

    private String buildContentSecurityPolicy() {
        Set<String> frameSources = new LinkedHashSet<>();
        frameSources.add("'self'");

        String supersetOrigin = supersetReportsEnabled ? normalizeOrigin(supersetBaseUrl) : null;
        if (supersetOrigin != null) {
            frameSources.add(supersetOrigin);
        }

        List<String> directives = new ArrayList<>();
        directives.add("default-src 'self'");
        directives.add("base-uri 'self'");
        directives.add("object-src 'none'");
        directives.add("frame-ancestors 'none'");
        directives.add("script-src 'self'");
        directives.add("style-src 'self' 'unsafe-inline'");
        directives.add("img-src 'self' data:");
        directives.add("font-src 'self' data:");
        directives.add("connect-src 'self'");
        directives.add("frame-src " + String.join(" ", frameSources));
        directives.add("form-action 'self'");
        return String.join("; ", directives);
    }

    private String normalizeOrigin(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return null;
        }
        try {
            URI uri = URI.create(rawUrl.trim());
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null || host == null) {
                return null;
            }
            String normalizedScheme = scheme.toLowerCase(Locale.ROOT);
            if (!"http".equals(normalizedScheme) && !"https".equals(normalizedScheme)) {
                return null;
            }
            int port = uri.getPort();
            return port > 0
                    ? normalizedScheme + "://" + host + ":" + port
                    : normalizedScheme + "://" + host;
        } catch (IllegalArgumentException _) {
            return null;
        }
    }
}
