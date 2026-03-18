package com.e24online.mdm.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("${api.version.prefix:v1}")
public class HealthController {

    @Value("${spring.application.name:online-mdm}")
    private String applicationName;

    @GetMapping("/health")
    public Mono<Map<String, Object>> health() {
        Map<String, Object> body = new HashMap<>();
        body.put("status", "UP");
        body.put("service", applicationName);
        body.put("timestamp", OffsetDateTime.now());
        return Mono.just(body);
    }
}

