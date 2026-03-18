package com.e24online.mdm.repository;

import com.e24online.mdm.domain.DeviceEnrollment;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DeviceEnrollmentRepository extends CrudRepository<DeviceEnrollment, Long> {

    @Query("""
            SELECT * FROM device_enrollment
            WHERE COALESCE(tenant_id, '') = COALESCE(:tenantId, '')
              AND status = COALESCE(:status, status)
              AND COALESCE(owner_user_id, -1) = COALESCE(:ownerUserId, COALESCE(owner_user_id, -1))
            ORDER BY enrolled_at DESC, id DESC
            LIMIT :limit OFFSET :offset
            """)
    List<DeviceEnrollment> findPagedByTenant(
            @Param("tenantId") String tenantId,
            @Param("status") String status,
            @Param("ownerUserId") Long ownerUserId,
            @Param("limit") int limit,
            @Param("offset") long offset
    );

    @Query("""
            SELECT * FROM device_enrollment
            WHERE id = :id
              AND COALESCE(tenant_id, '') = COALESCE(:tenantId, '')
            LIMIT 1
            """)
    Optional<DeviceEnrollment> findByIdAndTenant(
            @Param("id") Long id,
            @Param("tenantId") String tenantId
    );

    @Query("""
            SELECT * FROM device_enrollment
            WHERE COALESCE(tenant_id, '') = COALESCE(:tenantId, '')
              AND enrollment_no = :enrollmentNo
              AND status = 'ACTIVE'
            LIMIT 1
            """)
    Optional<DeviceEnrollment> findActiveByTenantAndEnrollmentNo(
            @Param("tenantId") String tenantId,
            @Param("enrollmentNo") String enrollmentNo
    );

    @Query("""
            SELECT COUNT(*) FROM device_enrollment
            WHERE COALESCE(tenant_id, '') = COALESCE(:tenantId, '')
              AND enrollment_no = :enrollmentNo
              AND owner_user_id = :ownerUserId
              AND status = 'ACTIVE'
            """)
    long countActiveByTenantAndEnrollmentNoAndOwnerUserId(
            @Param("tenantId") String tenantId,
            @Param("enrollmentNo") String enrollmentNo,
            @Param("ownerUserId") Long ownerUserId
    );

    @Query("""
            SELECT * FROM device_enrollment
            WHERE COALESCE(tenant_id, '') = COALESCE(:tenantId, '')
              AND enrollment_no = :enrollmentNo
            LIMIT 1
            """)
    Optional<DeviceEnrollment> findByTenantAndEnrollmentNo(
            @Param("tenantId") String tenantId,
            @Param("enrollmentNo") String enrollmentNo
    );
}
