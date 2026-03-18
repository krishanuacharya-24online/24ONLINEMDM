package com.e24online.mdm.repository;

import com.e24online.mdm.records.lookup.LookupRow;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class LookupJdbcRepository {

    private final JdbcTemplate jdbcTemplate;

    public LookupJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<String> listLookupTypes() {
        return jdbcTemplate.queryForList(
                "SELECT DISTINCT lookup_type FROM lkp_master ORDER BY lookup_type",
                String.class
        );
    }

    public List<LookupRow> listValues(String lookupType) {
        return jdbcTemplate.query(
                "SELECT lookup_type, code, description FROM lkp_master WHERE lookup_type = ? ORDER BY code",
                (rs, rowNum) -> new LookupRow(
                        rs.getString("lookup_type"),
                        rs.getString("code"),
                        rs.getString("description")
                ),
                lookupType
        );
    }

    public void upsertValue(String lookupType, String code, String description) {
        jdbcTemplate.update("""
                        INSERT INTO lkp_master (lookup_type, code, description)
                        VALUES (?, ?, ?)
                        ON CONFLICT (lookup_type, code)
                        DO UPDATE SET description = EXCLUDED.description
                        """,
                lookupType, code, description
        );
    }

    public int deleteValue(String lookupType, String code) {
        return jdbcTemplate.update(
                "DELETE FROM lkp_master WHERE lookup_type = ? AND code = ?",
                lookupType, code
        );
    }

}

