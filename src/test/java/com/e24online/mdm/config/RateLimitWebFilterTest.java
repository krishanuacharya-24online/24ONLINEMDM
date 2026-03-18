package com.e24online.mdm.config;

import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RateLimitWebFilterTest {

    private RateLimiterRegistry rateLimiterRegistry;
    private RateLimitWebFilter filter;

    @BeforeEach
    void setUp() {
        RateLimiterConfig config = RateLimiterConfig.custom()
                .limitForPeriod(1)
                .limitRefreshPeriod(Duration.ofMinutes(1))
                .timeoutDuration(Duration.ZERO)
                .build();
        rateLimiterRegistry = RateLimiterRegistry.of(config);
        filter = new RateLimitWebFilter(rateLimiterRegistry, new ApiVersionConfig("v1"));
    }

    @Test
    void nonTargetRequest_bypassesRateLimiter() {
        ServerWebExchange exchange = exchange(HttpMethod.GET, "/v1/agent/posture-payloads");
        AtomicInteger chainInvocations = new AtomicInteger();

        filter.filter(exchange, countedChain(chainInvocations)).block();

        assertEquals(1, chainInvocations.get());
        assertNull(exchange.getResponse().getStatusCode());
    }

    @Test
    void differentPath_bypassesRateLimiter() {
        ServerWebExchange exchange = exchange(HttpMethod.POST, "/v1/agent/other");
        AtomicInteger chainInvocations = new AtomicInteger();

        filter.filter(exchange, countedChain(chainInvocations)).block();

        assertEquals(1, chainInvocations.get());
        assertNull(exchange.getResponse().getStatusCode());
    }

    @Test
    void targetRequest_allowsWhenLimitAvailable() {
        ServerWebExchange exchange = exchange(HttpMethod.POST, "/v1/agent/posture-payloads");
        AtomicInteger chainInvocations = new AtomicInteger();

        filter.filter(exchange, countedChain(chainInvocations)).block();

        assertEquals(1, chainInvocations.get());
        assertNull(exchange.getResponse().getStatusCode());
    }

    @Test
    void targetRequest_returns429WhenNotPermitted() {
        ServerWebExchange exchange = exchange(HttpMethod.POST, "/v1/agent/posture-payloads");
        var limiter = rateLimiterRegistry.rateLimiter("ingest");
        WebFilterChain chain = ex -> Mono.error(RequestNotPermitted.createRequestNotPermitted(limiter));

        filter.filter(exchange, chain).block();

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exchange.getResponse().getStatusCode());
    }

    private WebFilterChain countedChain(AtomicInteger invocations) {
        return exchange -> {
            invocations.incrementAndGet();
            return exchange.getResponse().setComplete();
        };
    }

    private ServerWebExchange exchange(HttpMethod method, String path) {
        MockServerHttpRequest request = MockServerHttpRequest.method(method, path)
                .cookie(new HttpCookie("dummy", "x"))
                .build();
        return MockServerWebExchange.from(request);
    }
}
