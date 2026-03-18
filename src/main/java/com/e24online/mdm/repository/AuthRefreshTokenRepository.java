package com.e24online.mdm.repository;

import com.e24online.mdm.domain.AuthRefreshToken;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface AuthRefreshTokenRepository extends CrudRepository<AuthRefreshToken, Long> {

    Optional<AuthRefreshToken> findByJti(String jti);
    List<AuthRefreshToken> findByUserId(Long userId);

    @Query("""
            UPDATE auth_refresh_token
            SET revoked = true
            WHERE user_id = :userId
              AND expires_at < :now
            """)
    void revokeExpiredForUser(@Param("userId") Long userId, @Param("now") OffsetDateTime now);
}
