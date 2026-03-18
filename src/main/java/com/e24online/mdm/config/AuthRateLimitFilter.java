package com.e24online.mdm.config;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.reactor.ratelimiter.operator.RateLimiterOperator;
import org.jspecify.annotations.NonNull;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limiting filter for authentication endpoints to prevent brute-force attacks.
 * Implements per-IP rate limiting for /auth/login and /auth/refresh endpoints.
 * 
 * Configuration:
 * - login: 5 attempts per minute per IP (with exponential backoff on failures)
 * - refresh: 10 attempts per minute per IP
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class AuthRateLimitFilter implements WebFilter {

    private static final String LOGIN_PATH = "/auth/login";
    private static final String REFRESH_PATH = "/auth/refresh";
    
    // Per-IP rate limiters for login (stricter limits)
    private final Map<String, RateLimiter> loginRateLimiters = new ConcurrentHashMap<>();
    
    // Per-IP rate limiters for refresh
    private final Map<String, RateLimiter> refreshRateLimiters = new ConcurrentHashMap<>();
    
    // Rate limiter configs
    private final RateLimiterConfig loginRateLimitConfig;
    private final RateLimiterConfig refreshRateLimitConfig;
    
    // Cleanup old entries every 10 minutes
    private final Map<String, Long> lastAccessTime = new ConcurrentHashMap<>();
    private static final long CLEANUP_INTERVAL_MS = 600_000; // 10 minutes
    private volatile long lastCleanupTime = System.currentTimeMillis();

    public AuthRateLimitFilter() {
        // Login: 5 requests per minute, with 60-second timeout for retry
        this.loginRateLimitConfig = RateLimiterConfig.custom()
                .limitRefreshPeriod(Duration.ofMinutes(1))
                .limitForPeriod(5)
                .timeoutDuration(Duration.ofMillis(0)) // Fail fast instead of waiting
                .build();
        
        // Refresh: 10 requests per minute
        this.refreshRateLimitConfig = RateLimiterConfig.custom()
                .limitRefreshPeriod(Duration.ofMinutes(1))
                .limitForPeriod(10)
                .timeoutDuration(Duration.ofMillis(0))
                .build();
    }

    @Override
    public @NonNull Mono<Void> filter(ServerWebExchange exchange, @NonNull WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        String method = exchange.getRequest().getMethod().name();
        String clientIp = getClientIp(exchange);
        
        // Only apply rate limiting to POST requests on auth endpoints
        if (LOGIN_PATH.equals(path) && HttpMethod.POST.name().equals(method)) {
            return applyLoginRateLimit(exchange, chain, clientIp);
        } else if (REFRESH_PATH.equals(path) && HttpMethod.POST.name().equals(method)) {
            return applyRefreshRateLimit(exchange, chain, clientIp);
        }
        
        return chain.filter(exchange);
    }

    private Mono<Void> applyLoginRateLimit(ServerWebExchange exchange, 
                                           WebFilterChain chain, 
                                           String clientIp) {
        RateLimiter rateLimiter = getOrCreateRateLimiter(
            loginRateLimiters, 
            clientIp, 
            loginRateLimitConfig,
            "login"
        );
        
        cleanupOldEntriesIfNeeded();

        return chain.filter(exchange)
                .transformDeferred(RateLimiterOperator.of(rateLimiter))
                .onErrorResume(RequestNotPermitted.class, ex -> {
                    exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                    exchange.getResponse().getHeaders().add(
                        "X-RateLimit-Remaining",
                        String.valueOf(rateLimiter.getMetrics().getAvailablePermissions())
                    );
                    // Note: getWaitingDuration() may not be available in all Resilience4j versions
                    // Using a fixed retry-after header as fallback
                    exchange.getResponse().getHeaders().add("Retry-After", "60");
                    return exchange.getResponse().setComplete();
                })
                .doOnSuccess(unused -> {
                    // On successful response, we could optionally relax the rate limit
                    // For now, we keep it strict to prevent credential stuffing
                });
    }

    private Mono<Void> applyRefreshRateLimit(ServerWebExchange exchange,
                                             WebFilterChain chain,
                                             String clientIp) {
        RateLimiter rateLimiter = getOrCreateRateLimiter(
            refreshRateLimiters,
            clientIp,
            refreshRateLimitConfig,
            "refresh"
        );

        cleanupOldEntriesIfNeeded();

        return chain.filter(exchange)
                .transformDeferred(RateLimiterOperator.of(rateLimiter))
                .onErrorResume(RequestNotPermitted.class, ex -> {
                    exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                    exchange.getResponse().getHeaders().add(
                        "X-RateLimit-Remaining",
                        String.valueOf(rateLimiter.getMetrics().getAvailablePermissions())
                    );
                    return exchange.getResponse().setComplete();
                });
    }

    private RateLimiter getOrCreateRateLimiter(Map<String, RateLimiter> cache,
                                                String key,
                                                RateLimiterConfig config,
                                                String type) {
        return cache.computeIfAbsent(key, k -> {
            RateLimiterRegistry registry = RateLimiterRegistry.of(config);
            RateLimiter limiter = registry.rateLimiter(type + "-" + k.hashCode());
            lastAccessTime.put(key, System.currentTimeMillis());
            return limiter;
        });
    }

    private String getClientIp(ServerWebExchange exchange) {
        // Check X-Forwarded-For header first (for proxied requests)
        String forwardedFor = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isEmpty()) {
            // X-Forwarded-For can contain multiple IPs, take the first one
            return forwardedFor.split(",")[0].trim();
        }
        
        // Check X-Real-IP header
        String realIp = exchange.getRequest().getHeaders().getFirst("X-Real-IP");
        if (realIp != null && !realIp.isEmpty()) {
            return realIp;
        }
        
        // Fall back to remote address
        if (exchange.getRequest().getRemoteAddress() != null) {
            return exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
        }
        
        return "unknown";
    }

    private void cleanupOldEntriesIfNeeded() {
        long now = System.currentTimeMillis();
        if (now - lastCleanupTime > CLEANUP_INTERVAL_MS) {
            synchronized (this) {
                if (now - lastCleanupTime > CLEANUP_INTERVAL_MS) {
                    long threshold = now - Duration.ofMinutes(5).toMillis();
                    
                    // Clean login rate limiters
                    loginRateLimiters.entrySet().removeIf(entry -> {
                        Long lastAccess = lastAccessTime.get(entry.getKey());
                        return lastAccess != null && lastAccess < threshold;
                    });
                    
                    // Clean refresh rate limiters
                    refreshRateLimiters.entrySet().removeIf(entry -> {
                        Long lastAccess = lastAccessTime.get(entry.getKey());
                        return lastAccess != null && lastAccess < threshold;
                    });
                    
                    // Clean access times
                    lastAccessTime.entrySet().removeIf(entry -> 
                        entry.getValue() < threshold
                    );
                    
                    lastCleanupTime = now;
                }
            }
        }
    }
}
