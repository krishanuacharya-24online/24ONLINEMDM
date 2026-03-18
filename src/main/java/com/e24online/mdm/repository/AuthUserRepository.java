package com.e24online.mdm.repository;

import com.e24online.mdm.domain.AuthUser;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AuthUserRepository extends CrudRepository<AuthUser, Long> {

    Optional<AuthUser> findByUsernameAndIsDeletedFalse(String username);

    @Query("""
            SELECT * FROM auth_user
            WHERE id = :id
              AND tenant_id = :tenantId
              AND is_deleted = false
              AND status = 'ACTIVE'
            LIMIT 1
            """)
    Optional<AuthUser> findActiveByIdAndTenantId(
            @Param("id") Long id,
            @Param("tenantId") Long tenantId
    );

    @Query("""
            SELECT * FROM auth_user
            WHERE id = :id
              AND is_deleted = false
            LIMIT 1
            """)
    Optional<AuthUser> findActiveById(@Param("id") Long id);

    @Query("""
            SELECT * FROM auth_user
            WHERE is_deleted = false
              AND (:role IS NULL OR role = :role)
              AND (:status IS NULL OR status = :status)
              AND (:tenantId IS NULL OR tenant_id = :tenantId)
            ORDER BY id DESC
            LIMIT :limit OFFSET :offset
            """)
    List<AuthUser> findPaged(
            @Param("role") String role,
            @Param("status") String status,
            @Param("tenantId") Long tenantId,
            @Param("limit") int limit,
            @Param("offset") long offset
    );
}
