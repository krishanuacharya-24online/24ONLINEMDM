package com.e24online.mdm.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;

/**
 * Detects and logs available hardware resources at startup.
 * Helps diagnose auto-tuning behavior across different environments.
 */
@Configuration
public class ResourceDetectionConfig {

    private static final Logger log = LoggerFactory.getLogger(ResourceDetectionConfig.class);

    @Value("${HIKARI_MAX_POOL_SIZE:100}")
    private int hikariMaxPoolSize;

    @Value("${RATE_LIMIT_RPS:500}")
    private int rateLimitRps;

    @PostConstruct
    public void logSystemResources() {
        Runtime runtime = Runtime.getRuntime();
        RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean();

        int availableProcessors = runtime.availableProcessors();
        long maxMemory = runtime.maxMemory();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long uptime = runtimeMXBean.getUptime();

        log.info("=== HARDWARE RESOURCE DETECTION ===");
        log.info("Available CPU Cores: {} threads", availableProcessors);
        log.info("Max JVM Heap Memory: {} MB", maxMemory / (1024 * 1024));
        log.info("Allocated JVM Memory: {} MB", totalMemory / (1024 * 1024));
        log.info("Free Memory: {} MB", freeMemory / (1024 * 1024));
        log.info("JVM Uptime: {} ms", uptime);
        log.info("=== AUTO-TUNED CONFIGURATION ===");
        log.info("HikariCP Max Pool Size: {} connections", hikariMaxPoolSize);
        log.info("Rate Limit: {} RPS", rateLimitRps);
        log.info("Recommended Pool Size: {} (cores * 2 + 1)", (availableProcessors * 2) + 1);
        log.info("Recommended Rate Limit: {} RPS (cores * 100)", availableProcessors * 100);
    }
}
