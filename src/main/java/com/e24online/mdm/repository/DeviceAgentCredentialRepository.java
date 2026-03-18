package com.e24online.mdm.repository;

import com.e24online.mdm.domain.DeviceAgentCredential;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DeviceAgentCredentialRepository extends CrudRepository<DeviceAgentCredential, Long> {

    @Query("""
            SELECT * FROM device_agent_credential
            WHERE token_hash = :tokenHash
              AND status = 'ACTIVE'
            LIMIT 1
            """)
    Optional<DeviceAgentCredential> findActiveByTokenHash(@Param("tokenHash") String tokenHash);

    @Query("""
            SELECT * FROM device_agent_credential
            WHERE device_enrollment_id = :enrollmentId
              AND status = 'ACTIVE'
            """)
    List<DeviceAgentCredential> findActiveByEnrollmentId(@Param("enrollmentId") Long enrollmentId);
}
