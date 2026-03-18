package com.e24online.mdm.config;

import io.github.resilience4j.ratelimiter.RateLimiter;
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

/**
 * WebFlux-native rate limiting that protects Netty event-loops.
 * Blocking work is already offloaded to virtual threads; this filter prevents overload.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RateLimitWebFilter implements WebFilter {

    private final RateLimiter ingestRateLimiter;
    private final ApiVersionConfig apiVersionConfig;

    public RateLimitWebFilter(
            RateLimiterRegistry rateLimiterRegistry,
            ApiVersionConfig apiVersionConfig
    ) {
        this.ingestRateLimiter = rateLimiterRegistry.rateLimiter("ingest");
        this.apiVersionConfig = apiVersionConfig;
    }

    @Override
    public @NonNull Mono<Void> filter(ServerWebExchange exchange, @NonNull WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (apiVersionConfig.path("/agent/posture-payloads").equals(path)
                && HttpMethod.POST.equals(exchange.getRequest().getMethod())) {
            return chain.filter(exchange)
                    .transformDeferred(RateLimiterOperator.of(ingestRateLimiter))
                    .onErrorResume(RequestNotPermitted.class, ex -> {
                        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                        return exchange.getResponse().setComplete();
                    });
        }
        return chain.filter(exchange);
    }
}
