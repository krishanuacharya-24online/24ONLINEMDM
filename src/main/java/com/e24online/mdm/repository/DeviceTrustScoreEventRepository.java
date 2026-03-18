package com.e24online.mdm.repository;

import com.e24online.mdm.domain.DeviceTrustScoreEvent;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DeviceTrustScoreEventRepository extends CrudRepository<DeviceTrustScoreEvent, Long> {

    @Query("""
            SELECT e.* FROM device_trust_score_event e
            JOIN device_trust_profile p
              ON e.device_trust_profile_id = p.id
            WHERE p.device_external_id = :deviceExternalId
              AND COALESCE(p.tenant_id, '') = COALESCE(:tenantId, '')
            ORDER BY e.event_time DESC
            LIMIT :limit OFFSET :offset
            """)
    List<DeviceTrustScoreEvent> findByDevice(
            @Param("tenantId") String tenantId,
            @Param("deviceExternalId") String deviceExternalId,
            @Param("limit") int limit,
            @Param("offset") long offset
    );

    @Query("SELECT * FROM device_trust_score_event WHERE device_trust_profile_id = :profileId ORDER BY event_time DESC")
    List<DeviceTrustScoreEvent> findByDeviceTrustProfileIdOrderByEventTimeDesc(@Param("profileId") Long profileId);
}

