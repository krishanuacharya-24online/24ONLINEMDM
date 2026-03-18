package com.e24online.mdm.repository;

import com.e24online.mdm.domain.OsReleaseLifecycleMaster;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OsReleaseLifecycleMasterRepository extends CrudRepository<OsReleaseLifecycleMaster, Long> {

    @Query("""
            SELECT * FROM os_release_lifecycle_master
            WHERE is_deleted = false
              AND (:platformCode IS NULL OR platform_code = :platformCode)
            ORDER BY platform_code, cycle
            LIMIT :limit OFFSET :offset
            """)
    List<OsReleaseLifecycleMaster> findPaged(
            @Param("platformCode") String platformCode,
            @Param("limit") int limit,
            @Param("offset") long offset
    );

    @Query("""
            SELECT COUNT(*) FROM os_release_lifecycle_master
            WHERE is_deleted = false
              AND (:platformCode IS NULL OR platform_code = :platformCode)
            """)
    long countActive(@Param("platformCode") String platformCode);
}

