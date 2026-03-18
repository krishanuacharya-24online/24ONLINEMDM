package com.e24online.mdm.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Set;

/**
 * Service for checking if a password has been exposed in known data breaches
 * using the Have I Been Pwned (HIBP) API.
 * 
 * Uses the k-anonymity model: only sends first 5 characters of SHA-1 hash,
 * receives list of possible matches, and checks locally.
 * 
 * API: https://haveibeenpwned.com/API/v3#PwnedPasswords
 */
@Service
public class BreachedPasswordService {

    private static final Logger log = LoggerFactory.getLogger(BreachedPasswordService.class);
    
    // HIBP API endpoint
    private static final String HIBP_API_URL = "https://api.haveibeenpwned.com/range/";
    
    // Minimum breach count to consider a password as compromised
    private static final int MIN_BREACH_COUNT = 1;
    
    // Timeout for HIBP API calls
    private static final Duration API_TIMEOUT = Duration.ofSeconds(10);
    
    // Whether to enable breached password checking
    private final boolean enabled;
    
    // Optional API key for higher rate limits (not required for basic usage)
    private final String apiKey;
    
    private final WebClient webClient;

    public BreachedPasswordService(
            @Value("${security.password.hibp.enabled:false}") boolean enabled,
            @Value("${security.password.hibp.api-key:}") String apiKey
    ) {
        this.enabled = enabled;
        this.apiKey = apiKey;
        
        WebClient.Builder builder = WebClient.builder()
                .baseUrl(HIBP_API_URL)
                .defaultHeader(HttpHeaders.USER_AGENT, "24Online-MDM-Password-Checker/1.0")
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
        
        // Add API key header if provided (increases rate limit from 15/min to much higher)
        if (apiKey != null && !apiKey.isBlank()) {
            builder.defaultHeader("hibp-api-key", apiKey);
        }
        
        this.webClient = builder.build();
    }

    /**
     * Check if a password has been exposed in data breaches.
     * Uses k-anonymity: only sends first 5 chars of SHA-1 hash to API.
     * 
     * @param password The password to check
     * @return true if password was found in breaches, false otherwise
     */
    public boolean isBreached(String password) {
        if (!enabled) {
            // If service is disabled, don't check (fail open)
            // This allows the system to work without HIBP connectivity
            return false;
        }
        
        if (password == null || password.isEmpty()) {
            return false;
        }
        
        try {
            String sha1Hash = sha1Hex(password.toUpperCase());
            String prefix = sha1Hash.substring(0, 5).toUpperCase();
            String suffix = sha1Hash.substring(5).toUpperCase();
            
            // Query HIBP API with first 5 characters
            String response = webClient.get()
                    .uri(prefix)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(API_TIMEOUT)
                    .block();
            
            if (response == null) {
                log.warn("HIBP API returned null response");
                return false; // Fail open on API error
            }
            
            // Parse response (format: HASH_SUFFIX:COUNT per line)
            return parseHibpResponse(response, suffix);
            
        } catch (Exception e) {
            log.warn("Failed to check password against HIBP database: {}", e.getMessage());
            // Fail open - don't block login if HIBP is unavailable
            return false;
        }
    }

    /**
     * Async version of isBreached check.
     */
    public Mono<Boolean> isBreachedAsync(String password) {
        if (!enabled) {
            return Mono.just(false);
        }
        
        if (password == null || password.isEmpty()) {
            return Mono.just(false);
        }
        
        try {
            String sha1Hash = sha1Hex(password.toUpperCase());
            String prefix = sha1Hash.substring(0, 5).toUpperCase();
            String suffix = sha1Hash.substring(5).toUpperCase();
            
            return webClient.get()
                    .uri(prefix)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(API_TIMEOUT)
                    .map(response -> parseHibpResponse(response, suffix))
                    .onErrorReturn(false); // Fail open on error
        } catch (Exception e) {
            log.warn("Failed to check password against HIBP database: {}", e.getMessage());
            return Mono.just(false);
        }
    }

    /**
     * Get the breach count for a password (how many times it appeared in breaches).
     * 
     * @param password The password to check
     * @return breach count, or 0 if not found or error occurred
     */
    public int getBreachCount(String password) {
        if (!enabled || password == null || password.isEmpty()) {
            return 0;
        }
        
        try {
            String sha1Hash = sha1Hex(password.toUpperCase());
            String prefix = sha1Hash.substring(0, 5).toUpperCase();
            String suffix = sha1Hash.substring(5).toUpperCase();
            
            String response = webClient.get()
                    .uri(prefix)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(API_TIMEOUT)
                    .block();
            
            if (response == null) {
                return 0;
            }
            
            return parseBreachCount(response, suffix);
            
        } catch (Exception e) {
            log.warn("Failed to get breach count from HIBP: {}", e.getMessage());
            return 0;
        }
    }

    private boolean parseHibpResponse(String response, String targetSuffix) {
        Set<String> breachedHashes = extractBreachedHashes(response);
        return breachedHashes.contains(targetSuffix);
    }

    private int parseBreachCount(String response, String targetSuffix) {
        for (String line : response.split("\n")) {
            String[] parts = line.trim().split(":");
            if (parts.length == 2) {
                String hashSuffix = parts[0].trim().toUpperCase();
                if (hashSuffix.equals(targetSuffix)) {
                    try {
                        return Integer.parseInt(parts[1].trim());
                    } catch (NumberFormatException e) {
                        log.warn("Invalid breach count format: {}", parts[1]);
                        return 0;
                    }
                }
            }
        }
        return 0;
    }

    private Set<String> extractBreachedHashes(String response) {
        Set<String> hashes = new java.util.HashSet<>();
        for (String line : response.split("\n")) {
            String[] parts = line.trim().split(":");
            if (parts.length >= 1) {
                hashes.add(parts[0].trim().toUpperCase());
            }
        }
        return hashes;
    }

    private String sha1Hex(String input) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        byte[] digest = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest);
    }

    /**
     * Check if the HIBP service is enabled.
     */
    public boolean isEnabled() {
        return enabled;
    }
}
