package com.e24online.mdm.service;

import com.e24online.mdm.records.BreachCheckResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Local breached password checker - completely FREE offline alternative to HIBP.
 * Features:
 * 1. Checks against top 1000+ most common breached passwords (offline database)
 * 2. Pattern-based detection (sequences, repeats, keyboard patterns)
 * 3. Optional HIBP integration (free tier, no API key required)
 * This service provides defense-in-depth without any external API costs.
 */
@Service
public class LocalBreachedPasswordService {

    private static final Logger log = LoggerFactory.getLogger(LocalBreachedPasswordService.class);
    
    // Common patterns that indicate weak passwords
    private static final Pattern KEYBOARD_PATTERN = Pattern.compile(
        "(qwerty|qwertyuiop|asdf|asdfgh|asdfghjkl|zxcv|zxcvbn|zxcvbnm|1qaz|2wsx|3edc|4rfv|5tgb|6yhn|7ujm|8ik,|9ol.|0p;/|qazwsx|wsxedc|edcrfv|rfvtgb|tgbyhn|yhnujm|ujmik,|qweasd|asdzxc|123qwe|123qaz|abc123|123abc)",
        Pattern.CASE_INSENSITIVE
    );
    
    private static final Pattern SEQUENCE_PATTERN = Pattern.compile(
        "(012|123|234|345|456|567|678|789|890|901|abc|bcd|cde|def|efg|fgh|ghi|hij|ijk|jkl|klm|lmn|mno|nop|opq|pqr|qrs|rst|stu|tuv|uvw|vwx|wxy|xyz)",
        Pattern.CASE_INSENSITIVE
    );
    
    private static final Pattern REPEAT_PATTERN = Pattern.compile("(.)\\1{2,}");
    private static final Pattern DATE_PATTERN = Pattern.compile("(19|20)\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])");
    private static final Pattern SIMPLE_PATTERN = Pattern.compile("^(password|admin|user|login|welcome|hello|test|guest|master|root|pass|root|toor)$", Pattern.CASE_INSENSITIVE);
    
    // In-memory set of breached passwords
    private final Set<String> breachedPasswords = new HashSet<>();
    private final Set<String> breachedPasswordHashes = new HashSet<>();
    
    // Configuration
    private final boolean localCheckEnabled;
    private final boolean patternCheckEnabled;
    private final boolean hibpCheckEnabled;
    private final BreachedPasswordService hibpService;
    
    public LocalBreachedPasswordService(
            @Value("${security.password.local-check.enabled:true}") boolean localCheckEnabled,
            @Value("${security.password.pattern-check.enabled:true}") boolean patternCheckEnabled,
            @Value("${security.password.hibp.enabled:false}") boolean hibpCheckEnabled,
            BreachedPasswordService hibpService
    ) {
        this.localCheckEnabled = localCheckEnabled;
        this.patternCheckEnabled = patternCheckEnabled;
        this.hibpCheckEnabled = hibpCheckEnabled;
        this.hibpService = hibpService;
    }
    
    @PostConstruct
    public void init() {
        if (localCheckEnabled) {
            loadBreachedPasswords();
        }
    }
    
    /**
     * Load breached passwords from resource file.
     * This is a one-time operation at startup.
     */
    private void loadBreachedPasswords() {
        try {
            Resource resource = new org.springframework.core.io.ClassPathResource(
                "security/breached-passwords-top1000.txt"
            );
            
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                
                String line;
                int count = 0;
                while ((line = reader.readLine()) != null) {
                    line = line.trim().toLowerCase(Locale.ROOT);
                    
                    // Skip comments and empty lines
                    if (line.isEmpty() || line.startsWith("#")) {
                        continue;
                    }
                    
                    breachedPasswords.add(line);
                    breachedPasswordHashes.add(sha1Hex(line));
                    count++;
                }
                
                log.info("Loaded {} breached passwords from local database", count);
            }
            
        } catch (IOException e) {
            log.warn("Failed to load breached passwords database: {}. Pattern checking will still work.", 
                    e.getMessage());
        }
    }
    
    /**
     * Check if password is breached using all available methods.
     * 
     * @param password Password to check
     * @return true if password is breached or weak, false otherwise
     */
    public boolean isBreached(String password) {
        if (password == null || password.isEmpty()) {
            return true;
        }
        
        String lowerPassword = password.toLowerCase(Locale.ROOT);
        String passwordHash = sha1Hex(lowerPassword);
        
        // 1. Check local breached password database (FAST, FREE, OFFLINE)
        if (localCheckEnabled) {
            if (breachedPasswords.contains(lowerPassword)) {
                log.debug("Password found in local breached database");
                return true;
            }
            if (breachedPasswordHashes.contains(passwordHash)) {
                log.debug("Password hash found in local breached database");
                return true;
            }
        }
        
        // 2. Check for weak patterns (FAST, FREE, OFFLINE)
        if (patternCheckEnabled && hasWeakPattern(password)) {
            log.debug("Password has weak pattern");
            return true;
        }


        // 3. Optional: Check HIBP API (requires internet, FREE but rate-limited)
        if (hibpCheckEnabled && hibpService != null && hibpService.isEnabled()) {
            try {
                if (hibpService.isBreached(password)) {
                    log.debug("Password found in HIBP database");
                    return true;
                }
            } catch (Exception e) {
                log.warn("HIBP check failed (this is OK): {}", e.getMessage());
                // Fail open - don't block if HIBP is unavailable
            }
        }
        
        return false;
    }
    
    /**
     * Check if password has weak patterns.
     */
    private boolean hasWeakPattern(String password) {
        // Check for keyboard patterns
        if (KEYBOARD_PATTERN.matcher(password).find()) {
            return true;
        }
        
        // Check for sequential patterns
        if (SEQUENCE_PATTERN.matcher(password).find()) {
            return true;
        }
        
        // Check for repeated characters
        if (REPEAT_PATTERN.matcher(password).find()) {
            return true;
        }
        
        // Check for date patterns (YYYYMMDD)
        if (DATE_PATTERN.matcher(password).find()) {
            return true;
        }
        
        // Check for simple common passwords
        if (SIMPLE_PATTERN.matcher(password).matches()) {
            return true;
        }
        
        // Check for leet speak substitutions of common passwords
        return isLeetSpeakOfCommonPassword(password);
    }
    
    /**
     * Detect leet speak variations of common passwords.
     * e.g., p@ssw0rd, P455W0RD, etc.
     */
    private boolean isLeetSpeakOfCommonPassword(String password) {
        String normalized = password.toLowerCase(Locale.ROOT)
                .replace('@', 'a')
                .replace('0', 'o')
                .replace('1', 'i')
                .replace('3', 'e')
                .replace('$', 's')
                .replace('5', 's')
                .replace('7', 't')
                .replace('!', 'i')
                .replace('4', 'a');
        
        return breachedPasswords.contains(normalized) || 
               SIMPLE_PATTERN.matcher(normalized).matches();
    }
    
    /**
     * Get breach detection details for debugging/auditing.
     */
    public BreachCheckResult checkWithDetails(String password) {
        if (password == null || password.isEmpty()) {
            return new BreachCheckResult(true, "EMPTY_PASSWORD");
        }
        
        String lowerPassword = password.toLowerCase(Locale.ROOT);
        String passwordHash = sha1Hex(lowerPassword);
        
        // Check local database
        if (localCheckEnabled && breachedPasswords.contains(lowerPassword)) {
            return new BreachCheckResult(true, "FOUND_IN_LOCAL_DATABASE");
        }
        
        if (localCheckEnabled && breachedPasswordHashes.contains(passwordHash)) {
            return new BreachCheckResult(true, "FOUND_IN_LOCAL_DATABASE_HASH");
        }
        
        // Check patterns
        if (patternCheckEnabled) {
            if (KEYBOARD_PATTERN.matcher(password).find()) {
                return new BreachCheckResult(true, "KEYBOARD_PATTERN_DETECTED");
            }
            if (SEQUENCE_PATTERN.matcher(password).find()) {
                return new BreachCheckResult(true, "SEQUENCE_PATTERN_DETECTED");
            }
            if (REPEAT_PATTERN.matcher(password).find()) {
                return new BreachCheckResult(true, "REPEAT_PATTERN_DETECTED");
            }
            if (DATE_PATTERN.matcher(password).find()) {
                return new BreachCheckResult(true, "DATE_PATTERN_DETECTED");
            }
            if (SIMPLE_PATTERN.matcher(password).matches()) {
                return new BreachCheckResult(true, "COMMON_PASSWORD");
            }
            if (isLeetSpeakOfCommonPassword(password)) {
                return new BreachCheckResult(true, "LEET_SPEAK_COMMON_PASSWORD");
            }
        }
        
        // Check HIBP
        if (hibpCheckEnabled && hibpService != null && hibpService.isEnabled()) {
            try {
                if (hibpService.isBreached(password)) {
                    return new BreachCheckResult(true, "FOUND_IN_HIBP_DATABASE");
                }
            } catch (Exception e) {
                log.debug("HIBP check skipped: {}", e.getMessage());
            }
        }
        
        return new BreachCheckResult(false, "PASSWORD_ACCEPTABLE");
    }
    
    private String sha1Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString().toUpperCase(Locale.ROOT);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-1 algorithm not available", e);
        }
    }
    

}
