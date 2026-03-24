package com.e24online.mdm.service;

import com.e24online.mdm.domain.AuthRefreshToken;
import com.e24online.mdm.repository.AuthRefreshTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Service responsible for automatically revoking expired refresh tokens.
 * This prevents accumulation of expired tokens in the database and ensures
 * consistent token state management.
 */
@Service
public class ExpiredTokenRevocationService {

    private static final Logger log = LoggerFactory.getLogger(ExpiredTokenRevocationService.class);

    private final AuthRefreshTokenRepository refreshRepository;
    private final BlockingDb blockingDb;

    public ExpiredTokenRevocationService(AuthRefreshTokenRepository refreshRepository, BlockingDb blockingDb) {
        this.refreshRepository = refreshRepository;
        this.blockingDb = blockingDb;
    }

    /**
     * Scheduled task that runs daily at 2 AM to revoke all expired refresh tokens.
     * This prevents the database from accumulating expired tokens and ensures
     * that expired tokens are properly marked as revoked for audit purposes.
     * Cron: sec min hour day month weekday
     * Default: 0 0 2 * * * = every day at 2:00 AM
     */
    @Scheduled(cron = "${mdm.token.revocation.cron:0 0 2 * * *}")
    public void revokeExpiredTokensScheduled() {
        blockingDb.run(() -> {
            int revokedCount = revokeAllExpiredTokensInternal();
            log.info("Scheduled expired token revocation completed: {} tokens revoked", revokedCount);
        }).block();
    }

    /**
     * Manually revoke all expired refresh tokens for a specific user.
     * This is called during login to clean up expired tokens when the user authenticates.
     *
     * @param userId the user ID whose expired tokens should be revoked
     * @return number of tokens revoked
     */
    @Transactional
    public int revokeExpiredTokensForUser(Long userId) {
        if (userId == null) {
            return 0;
        }

        OffsetDateTime now = OffsetDateTime.now();
        int revokedCount = 0;

        try {
            List<AuthRefreshToken> userTokens = refreshRepository.findByUserId(userId);
            List<AuthRefreshToken> tokensToSave = new ArrayList<>();

            for (AuthRefreshToken token : userTokens) {
                if (!token.isRevoked() && token.getExpiresAt().isBefore(now)) {
                    token.setRevoked(true);
                    tokensToSave.add(token);
                    revokedCount++;
                }
            }

            // Batch save all revoked tokens
            for (AuthRefreshToken token : tokensToSave) {
                refreshRepository.save(token);
            }

            if (revokedCount > 0) {
                log.debug("Revoked {} expired tokens for user {}", revokedCount, userId);
            }

            return revokedCount;
        } catch (Exception e) {
            log.error("Failed to revoke expired tokens for user {}: {}", userId, e.getMessage(), e);
            return 0;
        }
    }

    private int revokeAllExpiredTokensInternal() {
        OffsetDateTime now = OffsetDateTime.now();
        int revokedCount = 0;

        try {
            List<AuthRefreshToken> tokensToSave = new ArrayList<>();

            // Get all tokens and revoke expired ones
            for (AuthRefreshToken token : refreshRepository.findAll()) {
                if (!token.isRevoked() && token.getExpiresAt().isBefore(now)) {
                    token.setRevoked(true);
                    tokensToSave.add(token);
                    revokedCount++;
                }
            }

            // Batch save all revoked tokens
            for (AuthRefreshToken token : tokensToSave) {
                refreshRepository.save(token);
            }

            return revokedCount;
        } catch (Exception e) {
            log.error("Failed to revoke expired tokens: {}", e.getMessage(), e);
            return 0;
        }
    }
}
