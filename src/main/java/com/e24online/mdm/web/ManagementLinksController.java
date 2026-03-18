package com.e24online.mdm.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import reactor.core.publisher.Mono;

@Controller
@RequestMapping("/ui/management")
@PreAuthorize("hasRole('PRODUCT_ADMIN')")
public class ManagementLinksController {

    private final String zipkinUiUrl;
    private final String rabbitMqUiUrl;

    public ManagementLinksController(
            @Value("${management.zipkin.ui-url:http://localhost:9411}") String zipkinUiUrl,
            @Value("${management.rabbitmq.ui-url:http://localhost:15672}") String rabbitMqUiUrl
    ) {
        this.zipkinUiUrl = normalizeUiUrl(zipkinUiUrl, "http://localhost:9411");
        this.rabbitMqUiUrl = normalizeUiUrl(rabbitMqUiUrl, "http://localhost:15672");
    }

    @GetMapping("/swagger")
    public Mono<String> swagger() {
        return Mono.just("redirect:/swagger-ui.html");
    }

    @GetMapping("/zipkin")
    public Mono<String> zipkin() {
        return Mono.just("redirect:" + zipkinUiUrl);
    }

    @GetMapping("/rabbitmq")
    public Mono<String> rabbitmq() {
        return Mono.just("redirect:" + rabbitMqUiUrl);
    }

    private static String normalizeUiUrl(String rawUrl, String fallback) {
        if (rawUrl == null) {
            return fallback;
        }
        String value = rawUrl.trim();
        return value.isEmpty() ? fallback : value;
    }
}
