package com.e24online.mdm.repository;

import com.e24online.mdm.domain.ApplicationCatalogEntry;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ApplicationCatalogRepository extends CrudRepository<ApplicationCatalogEntry, Long> {

    @Query("""
            SELECT * FROM application_catalog
            WHERE id = :id
              AND is_deleted = false
            LIMIT 1
            """)
    Optional<ApplicationCatalogEntry> findActiveById(@Param("id") Long id);

    @Query("""
            SELECT * FROM application_catalog
            WHERE is_deleted = false
              AND (CAST(:osType AS VARCHAR) IS NULL OR os_type = CAST(:osType AS VARCHAR))
              AND (COALESCE(CAST(:search AS VARCHAR), '') = ''
                   OR lower(app_name) LIKE ('%' || lower(CAST(:search AS VARCHAR)) || '%')
                   OR lower(COALESCE(package_id, '')) LIKE ('%' || lower(CAST(:search AS VARCHAR)) || '%'))
            ORDER BY app_name
            LIMIT :limit OFFSET :offset
            """)
    List<ApplicationCatalogEntry> findPaged(
            @Param("osType") String osType,
            @Param("search") String search,
            @Param("limit") int limit,
            @Param("offset") long offset
    );

    @Query("""
            SELECT COUNT(*) FROM application_catalog
            WHERE is_deleted = false
              AND (CAST(:osType AS VARCHAR) IS NULL OR os_type = CAST(:osType AS VARCHAR))
              AND (COALESCE(CAST(:search AS VARCHAR), '') = ''
                   OR lower(app_name) LIKE ('%' || lower(CAST(:search AS VARCHAR)) || '%')
                   OR lower(COALESCE(package_id, '')) LIKE ('%' || lower(CAST(:search AS VARCHAR)) || '%'))
            """)
    long countFiltered(
            @Param("osType") String osType,
            @Param("search") String search
    );
}
