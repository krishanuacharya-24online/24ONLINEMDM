package com.e24online.mdm.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActuatorAccessWebFilterTest {

    @Test
    void allowsNonActuatorPath() {
        ActuatorAccessWebFilter filter = new ActuatorAccessWebFilter("127.0.0.1/32");
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/v1/devices").build()
        );

        AtomicBoolean chainCalled = new AtomicBoolean(false);
        WebFilterChain chain = webExchange -> {
            chainCalled.set(true);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertTrue(chainCalled.get());
    }

    @Test
    void allowsProbePathWithoutCidrCheck() {
        ActuatorAccessWebFilter filter = new ActuatorAccessWebFilter("10.0.0.0/8");
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/actuator/health/liveness").build()
        );

        AtomicBoolean chainCalled = new AtomicBoolean(false);
        WebFilterChain chain = webExchange -> {
            chainCalled.set(true);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertTrue(chainCalled.get());
    }

    @Test
    void blocksActuatorPathWhenIpIsNotAllowlisted() {
        ActuatorAccessWebFilter filter = new ActuatorAccessWebFilter("10.0.0.0/8");
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/actuator/prometheus")
                        .remoteAddress(new InetSocketAddress("203.0.113.10", 51000))
                        .build()
        );

        WebFilterChain chain = webExchange -> Mono.empty();
        filter.filter(exchange, chain).block();

        assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
    }

    @Test
    void allowsActuatorPathWhenIpIsAllowlisted() {
        ActuatorAccessWebFilter filter = new ActuatorAccessWebFilter("203.0.113.0/24");
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/actuator/prometheus")
                        .remoteAddress(new InetSocketAddress("203.0.113.42", 51000))
                        .build()
        );

        AtomicBoolean chainCalled = new AtomicBoolean(false);
        WebFilterChain chain = webExchange -> {
            chainCalled.set(true);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertTrue(chainCalled.get());
    }
}
