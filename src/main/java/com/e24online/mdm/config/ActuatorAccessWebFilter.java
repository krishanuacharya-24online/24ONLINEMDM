package com.e24online.mdm.config;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

/**
 * Restricts actuator endpoints to configured CIDR ranges.
 * Liveness and readiness probe endpoints remain publicly reachable.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class ActuatorAccessWebFilter implements WebFilter {

    private static final Logger log = LoggerFactory.getLogger(ActuatorAccessWebFilter.class);

    private final List<CidrBlock> allowedCidrs;

    public ActuatorAccessWebFilter(
            @Value("${management.security.allowed-cidrs:127.0.0.1/32,::1/128}") String allowedCidrs
    ) {
        this.allowedCidrs = parseCidrList(allowedCidrs);
        if (this.allowedCidrs.isEmpty()) {
            log.warn("No management.security.allowed-cidrs configured. /actuator/** endpoints are blocked except probe paths.");
        } else {
            log.info("Actuator CIDR allowlist loaded with {} entr(y/ies).", this.allowedCidrs.size());
        }
    }

    @Override
    public @NonNull Mono<Void> filter(ServerWebExchange exchange, @NonNull WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (!path.startsWith("/actuator")) {
            return chain.filter(exchange);
        }
        if (isPublicProbePath(path)) {
            return chain.filter(exchange);
        }

        InetAddress remoteAddress = exchange.getRequest().getRemoteAddress() != null
                ? exchange.getRequest().getRemoteAddress().getAddress()
                : null;
        if (remoteAddress == null || !isAllowed(remoteAddress)) {
            exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
            return exchange.getResponse().setComplete();
        }
        return chain.filter(exchange);
    }

    private boolean isAllowed(InetAddress remoteAddress) {
        for (CidrBlock cidr : allowedCidrs) {
            if (cidr.contains(remoteAddress)) {
                return true;
            }
        }
        return false;
    }

    private boolean isPublicProbePath(String path) {
        return "/actuator/health/liveness".equals(path)
                || "/actuator/health/readiness".equals(path);
    }

    private List<CidrBlock> parseCidrList(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        String[] tokens = raw.trim().split("[,;\\s]+");
        List<CidrBlock> cidrs = new ArrayList<>();
        for (String token : tokens) {
            if (token == null || token.isBlank()) {
                continue;
            }
            try {
                cidrs.add(parseCidr(token.trim()));
            } catch (IllegalArgumentException ex) {
                log.warn("Skipping invalid actuator CIDR '{}' : {}", token, ex.getMessage());
            }
        }
        return List.copyOf(cidrs);
    }

    private CidrBlock parseCidr(String value) {
        String addressPart = value;
        Integer prefixLength = null;

        int slashIndex = value.indexOf('/');
        if (slashIndex >= 0) {
            addressPart = value.substring(0, slashIndex).trim();
            String prefixPart = value.substring(slashIndex + 1).trim();
            if (prefixPart.isBlank()) {
                throw new IllegalArgumentException("CIDR prefix is missing");
            }
            try {
                prefixLength = Integer.parseInt(prefixPart);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("CIDR prefix is not numeric");
            }
        }

        try {
            InetAddress inetAddress = InetAddress.getByName(addressPart);
            byte[] network = inetAddress.getAddress();
            int maxPrefix = network.length * 8;
            int resolvedPrefix = prefixLength == null ? maxPrefix : prefixLength;
            if (resolvedPrefix < 0 || resolvedPrefix > maxPrefix) {
                throw new IllegalArgumentException("CIDR prefix out of range for address family");
            }
            return new CidrBlock(network, resolvedPrefix);
        } catch (UnknownHostException ex) {
            throw new IllegalArgumentException("Invalid CIDR address");
        }
    }

    private record CidrBlock(byte[] network, int prefixLength) {

        private boolean contains(InetAddress address) {
            byte[] candidate = address.getAddress();
            if (candidate.length != network.length) {
                return false;
            }
            int fullBytes = prefixLength / 8;
            int remainingBits = prefixLength % 8;

            for (int i = 0; i < fullBytes; i++) {
                if (network[i] != candidate[i]) {
                    return false;
                }
            }
            if (remainingBits == 0) {
                return true;
            }
            int mask = (0xFF << (8 - remainingBits)) & 0xFF;
            return (network[fullBytes] & mask) == (candidate[fullBytes] & mask);
        }
    }
}
