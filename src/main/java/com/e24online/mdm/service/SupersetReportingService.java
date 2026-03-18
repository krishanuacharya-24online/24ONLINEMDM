package com.e24online.mdm.service;

import com.e24online.mdm.config.SupersetReportingProperties;
import com.e24online.mdm.records.EmbedConfig;
import com.e24online.mdm.records.user.guest.GuestToken;
import com.e24online.mdm.records.user.UserPrincipal;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class SupersetReportingService {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {};
    private static final TypeReference<Map<String, Object>> JACKSON_MAP_TYPE =
            new TypeReference<>() {};

    private final SupersetReportingProperties properties;
    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    public SupersetReportingService(SupersetReportingProperties properties,
                                    WebClient.Builder webClientBuilder,
                                    ObjectMapper objectMapper) {
        this.properties = properties;
        this.webClientBuilder = webClientBuilder;
        this.objectMapper = objectMapper;
    }

    public EmbedConfig embedConfig() {
        if (!properties.isEnabled()) {
            return new EmbedConfig(false, null, null, false,
                    "Superset reporting is disabled.", null, null, null);
        }

        String supersetDomain = normalizeBaseUrl(properties.getBaseUrl());
        String dashboardPath = normalizeDashboardPath(properties.getDashboardPath());
        String iframeUrl = (supersetDomain == null || dashboardPath == null) ? null : supersetDomain + dashboardPath;
        String embeddedDashboardId = firstNonBlank(
                normalizeOptionalText(properties.getEmbeddedDashboardId()),
                normalizeOptionalText(properties.getResourceId())
        );
        String resourceId = firstNonBlank(
                normalizeOptionalText(properties.getResourceId()),
                embeddedDashboardId
        );

        if (properties.isGuestTokenEnabled()) {
            boolean missingGuestTokenInputs =
                    supersetDomain == null ||
                    embeddedDashboardId == null ||
                    resourceId == null ||
                    isBlank(properties.getUsername()) ||
                    isBlank(properties.getPassword());
            if (missingGuestTokenInputs) {
                return new EmbedConfig(false, iframeUrl, normalizeOptionalText(properties.getIframeSandbox()),
                        true,
                        "Guest-token mode requires reports.superset.base-url, embedded-dashboard-id (or resource-id), username and password.",
                        supersetDomain, resourceId, embeddedDashboardId);
            }
        }

        if (iframeUrl == null) {
            return new EmbedConfig(false, null, null, properties.isGuestTokenEnabled(),
                    "Superset reporting is not configured. Set reports.superset.base-url and reports.superset.dashboard-path.",
                    supersetDomain, resourceId, embeddedDashboardId);
        }

        return new EmbedConfig(
                true,
                iframeUrl,
                normalizeOptionalText(properties.getIframeSandbox()),
                properties.isGuestTokenEnabled(),
                "Superset dashboard is ready.",
                supersetDomain,
                resourceId,
                embeddedDashboardId
        );
    }

    public Mono<GuestToken> createGuestToken(UserPrincipal principal, String tenantId) {
        if (!properties.isEnabled()) {
            return Mono.error(new IllegalStateException("Superset reporting is disabled."));
        }
        if (!properties.isGuestTokenEnabled()) {
            return Mono.error(new IllegalStateException("Superset guest-token mode is disabled."));
        }
        if (isBlank(properties.getResourceId()) && isBlank(properties.getEmbeddedDashboardId())) {
            return Mono.error(new IllegalStateException(
                    "reports.superset.resource-id or reports.superset.embedded-dashboard-id is required for guest token generation."));
        }
        if (isBlank(properties.getUsername()) || isBlank(properties.getPassword())) {
            return Mono.error(new IllegalStateException("reports.superset.username and reports.superset.password are required for guest token generation."));
        }
        String normalizedBaseUrl = normalizeBaseUrl(properties.getBaseUrl());
        if (normalizedBaseUrl == null) {
            return Mono.error(new IllegalStateException("reports.superset.base-url is invalid."));
        }

        WebClient webClient = webClientBuilder
                .baseUrl(normalizedBaseUrl)
                .build();

        return fetchAccessToken(webClient)
                .flatMap(accessToken -> resolveGuestResourceId(webClient, accessToken)
                        .flatMap(resourceId -> fetchCsrfToken(webClient, accessToken)
                                .defaultIfEmpty("")
                                .flatMap(csrfToken -> requestGuestToken(
                                                webClient, accessToken, csrfToken, principal, tenantId, resourceId)
                                        .map(token -> new GuestToken(token, resourceId)))))
                .switchIfEmpty(Mono.error(new IllegalStateException("Superset guest token request returned no response.")));
    }

    private Mono<String> fetchAccessToken(WebClient webClient) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("username", normalizeOptionalText(properties.getUsername()));
        payload.put("password", properties.getPassword());
        payload.put("provider", defaultString(normalizeOptionalText(properties.getAuthProvider()), "db"));
        payload.put("refresh", true);

        return webClient.post()
                .uri("/api/v1/security/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .exchangeToMono(response -> {
                    if (!response.statusCode().is2xxSuccessful()) {
                        return response.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .flatMap(body -> Mono.error(new IllegalStateException(
                                        "Superset login failed with status " + response.statusCode().value() + formatBodyHint(body))));
                    }
                    return response.bodyToMono(MAP_TYPE)
                            .map(map -> normalizeOptionalText(asText(map.get("access_token"))))
                            .flatMap(accessToken -> accessToken == null
                                    ? Mono.error(new IllegalStateException("Superset login response is missing access_token."))
                                    : Mono.just(accessToken));
                });
    }

    private Mono<String> fetchCsrfToken(WebClient webClient, String accessToken) {
        return webClient.get()
                .uri("/api/v1/security/csrf_token/")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .exchangeToMono(response -> {
                    if (!response.statusCode().is2xxSuccessful()) {
                        return Mono.empty();
                    }
                    return response.bodyToMono(MAP_TYPE)
                            .map(body -> normalizeOptionalText(asText(body.get("result"))))
                            .flatMap(token -> token == null ? Mono.empty() : Mono.just(token));
                });
    }

    private Mono<String> requestGuestToken(WebClient webClient,
                                           String accessToken,
                                           String csrfToken,
                                           UserPrincipal principal,
                                           String tenantId,
                                           String resourceId) {
        Map<String, Object> body = buildGuestTokenRequest(principal, tenantId, resourceId);
        if (body == null) {
            return Mono.error(new IllegalStateException("Superset guest token request cannot be created from current configuration."));
        }

        return webClient.post()
                .uri("/api/v1/security/guest_token/")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .headers(headers -> {
                    if (!isBlank(csrfToken)) {
                        headers.set("X-CSRFToken", csrfToken);
                    }
                    String referer = normalizeBaseUrl(properties.getBaseUrl());
                    if (referer != null) {
                        headers.set(HttpHeaders.REFERER, referer);
                    }
                })
                .bodyValue(body)
                .exchangeToMono(response -> {
                    return response.bodyToMono(String.class)
                            .defaultIfEmpty("")
                            .flatMap(raw -> {
                                if (!response.statusCode().is2xxSuccessful()) {
                                    return Mono.error(new IllegalStateException(
                                            "Superset guest token request failed with status "
                                                    + response.statusCode().value() + formatBodyHint(raw)));
                                }
                                if (isBlank(raw)) {
                                    return Mono.error(new IllegalStateException("Superset guest token response is empty."));
                                }

                                Map<String, Object> payload;
                                try {
                                    payload = objectMapper.readValue(raw, JACKSON_MAP_TYPE);
                                } catch (Exception ex) {
                                    return Mono.error(new IllegalStateException(
                                            "Superset guest token response is not valid JSON." + formatBodyHint(raw)));
                                }

                                String token = extractGuestToken(payload);
                                if (token == null) {
                                    return Mono.error(new IllegalStateException(
                                            "Superset guest token response is missing token." + formatBodyHint(raw)));
                                }
                                return Mono.just(token);
                            });
                });
    }

    private Mono<String> resolveGuestResourceId(WebClient webClient, String accessToken) {
        String configuredResourceId = firstNonBlank(
                normalizeOptionalText(properties.getResourceId()),
                normalizeOptionalText(properties.getEmbeddedDashboardId())
        );
        if (configuredResourceId == null) {
            return Mono.error(new IllegalStateException(
                    "reports.superset.resource-id or reports.superset.embedded-dashboard-id is required for guest token generation."));
        }
        if (configuredResourceId.chars().allMatch(Character::isDigit)) {
            return ensureEmbeddedDashboardId(webClient, accessToken, configuredResourceId);
        }
        return Mono.just(configuredResourceId);
    }

    private Mono<String> ensureEmbeddedDashboardId(WebClient webClient, String accessToken, String dashboardId) {
        return webClient.get()
                .uri("/api/v1/dashboard/{id}/embedded", dashboardId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .exchangeToMono(response -> {
                    if (response.statusCode().is2xxSuccessful()) {
                        return response.bodyToMono(MAP_TYPE).flatMap(this::extractEmbeddedDashboardId);
                    }
                    if (response.statusCode().value() == 404) {
                        return createEmbeddedDashboard(webClient, accessToken, dashboardId);
                    }
                    return response.bodyToMono(String.class)
                            .defaultIfEmpty("")
                            .flatMap(body -> Mono.error(new IllegalStateException(
                                    "Superset embedded dashboard lookup failed with status "
                                            + response.statusCode().value() + formatBodyHint(body))));
                });
    }

    private Mono<String> createEmbeddedDashboard(WebClient webClient, String accessToken, String dashboardId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("allowed_domains", parseEmbeddedAllowedDomains());

        return webClient.post()
                .uri("/api/v1/dashboard/{id}/embedded", dashboardId)
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .headers(headers -> {
                    String referer = normalizeBaseUrl(properties.getBaseUrl());
                    if (referer != null) {
                        headers.set(HttpHeaders.REFERER, referer);
                    }
                })
                .bodyValue(payload)
                .exchangeToMono(response -> {
                    if (!response.statusCode().is2xxSuccessful()) {
                        return response.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .flatMap(body -> Mono.error(new IllegalStateException(
                                        "Superset embedded dashboard creation failed with status "
                                                + response.statusCode().value() + formatBodyHint(body))));
                    }
                    return response.bodyToMono(MAP_TYPE).flatMap(this::extractEmbeddedDashboardId);
                });
    }

    private List<String> parseEmbeddedAllowedDomains() {
        String raw = normalizeOptionalText(properties.getEmbeddedAllowedDomains());
        if (raw == null) {
            return List.of("http://localhost:8080", "http://127.0.0.1:8080");
        }
        List<String> parsed = List.of(raw.split(",")).stream()
                .map(this::normalizeOptionalText)
                .filter(value -> value != null)
                .distinct()
                .toList();
        return parsed.isEmpty()
                ? List.of("http://localhost:8080", "http://127.0.0.1:8080")
                : parsed;
    }

    private Mono<String> extractEmbeddedDashboardId(Map<String, Object> body) {
        Object resultRaw = body.get("result");
        if (!(resultRaw instanceof Map<?, ?> result)) {
            return Mono.error(new IllegalStateException("Superset embedded dashboard response is missing result."));
        }
        String embeddedId = normalizeOptionalText(asText(result.get("uuid")));
        if (embeddedId == null) {
            return Mono.error(new IllegalStateException("Superset embedded dashboard response is missing uuid."));
        }
        return Mono.just(embeddedId);
    }

    private Map<String, Object> buildGuestTokenRequest(UserPrincipal principal, String tenantId, String resourceId) {
        String resourceType = defaultString(normalizeOptionalText(properties.getResourceType()), "dashboard");
        if (resourceId == null) {
            return null;
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("resources", List.of(Map.of(
                "type", resourceType,
                "id", resourceId
        )));

        String rlsClause = resolveRlsClause(principal, tenantId);
        payload.put("rls", rlsClause == null ? List.of() : List.of(Map.of("clause", rlsClause)));

        String username = principal == null ? "mdm-user" : defaultString(normalizeOptionalText(principal.username()), "mdm-user");
        String role = principal == null ? "USER" : defaultString(normalizeOptionalText(principal.role()), "USER");
        payload.put("user", Map.of(
                "username", username,
                "first_name", username,
                "last_name", role
        ));
        return payload;
    }

    private String resolveRlsClause(UserPrincipal principal, String tenantId) {
        String template = normalizeOptionalText(properties.getTenantRlsClauseTemplate());
        if (template == null) {
            return null;
        }

        String resolved = template;
        if (tenantId != null) {
            resolved = resolved.replace("{{tenantId}}", escapeSqlLiteral(tenantId));
        }
        if (principal != null && principal.tenantId() != null) {
            resolved = resolved.replace("{{tenantPk}}", String.valueOf(principal.tenantId()));
        }

        if (resolved.contains("{{") || resolved.contains("}}")) {
            return null;
        }
        return resolved;
    }

    private String buildIframeUrl() {
        String baseUrl = normalizeBaseUrl(properties.getBaseUrl());
        String dashboardPath = normalizeDashboardPath(properties.getDashboardPath());
        if (baseUrl == null || dashboardPath == null) {
            return null;
        }
        return baseUrl + dashboardPath;
    }

    private String normalizeBaseUrl(String value) {
        String normalized = normalizeOptionalText(value);
        if (normalized == null) {
            return null;
        }
        try {
            URI uri = URI.create(normalized);
            if (uri.getScheme() == null || uri.getHost() == null) {
                return null;
            }
            String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
            if (!"http".equals(scheme) && !"https".equals(scheme)) {
                return null;
            }
            if (normalized.endsWith("/")) {
                return normalized.substring(0, normalized.length() - 1);
            }
            return normalized;
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String normalizeDashboardPath(String value) {
        String normalized = normalizeOptionalText(value);
        if (normalized == null) {
            return null;
        }
        return normalized.startsWith("/") ? normalized : "/" + normalized;
    }

    private String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String defaultString(String value, String fallback) {
        return value == null ? fallback : value;
    }

    private String firstNonBlank(String first, String second) {
        return first != null ? first : second;
    }

    private String asText(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String extractGuestToken(Map<String, Object> payload) {
        if (payload == null) {
            return null;
        }
        String token = normalizeOptionalText(asText(payload.get("token")));
        if (token != null) {
            return token;
        }

        Object result = payload.get("result");
        if (result instanceof Map<?, ?> nested) {
            token = normalizeOptionalText(asText(nested.get("token")));
            if (token != null) {
                return token;
            }
        }

        Object data = payload.get("data");
        if (data instanceof Map<?, ?> nested) {
            token = normalizeOptionalText(asText(nested.get("token")));
            if (token != null) {
                return token;
            }
        }
        return null;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String escapeSqlLiteral(String value) {
        return value == null ? null : value.replace("'", "''");
    }

    private String formatBodyHint(String body) {
        String trimmed = normalizeOptionalText(body);
        if (trimmed == null) {
            return "";
        }
        return ". Response: " + trimmed;
    }

}
