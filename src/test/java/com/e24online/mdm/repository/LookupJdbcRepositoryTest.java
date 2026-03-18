package com.e24online.mdm.repository;

import com.e24online.mdm.records.lookup.LookupRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LookupJdbcRepositoryTest {

    private JdbcTemplate jdbcTemplate;
    private LookupJdbcRepository repository;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        repository = new LookupJdbcRepository(jdbcTemplate);
    }

    @Test
    void listLookupTypes_queriesDistinctTypes() {
        when(jdbcTemplate.queryForList(anyString(), eq(String.class))).thenReturn(List.of("os_type", "app_status"));

        List<String> result = repository.listLookupTypes();

        assertEquals(List.of("os_type", "app_status"), result);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForList(sql.capture(), eq(String.class));
        assertTrue(sql.getValue().contains("SELECT DISTINCT lookup_type"));
    }

    @Test
    void listValues_mapsRowsToLookupRow() throws Exception {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq("os_type"))).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            RowMapper<LookupRow> mapper = (RowMapper<LookupRow>) invocation.getArguments()[1];
            ResultSet rs = mock(ResultSet.class);
            when(rs.getString("lookup_type")).thenReturn("os_type");
            when(rs.getString("code")).thenReturn("win");
            when(rs.getString("description")).thenReturn("Windows");
            return List.of(mapper.mapRow(rs, 0));
        });

        List<LookupRow> rows = repository.listValues("os_type");

        assertEquals(1, rows.size());
        LookupRow row = rows.getFirst();
        assertEquals("os_type", row.lookupType());
        assertEquals("win", row.code());
        assertEquals("Windows", row.description());
    }

    @Test
    void upsertValue_executesConflictUpdateStatement() {
        repository.upsertValue("os_type", "win", "Windows");

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(sql.capture(), eq("os_type"), eq("win"), eq("Windows"));
        String normalized = sql.getValue().replaceAll("\\s+", " ").trim();
        assertTrue(normalized.startsWith("INSERT INTO lkp_master"));
        assertTrue(normalized.contains("ON CONFLICT (lookup_type, code)"));
        assertTrue(normalized.contains("DO UPDATE SET description = EXCLUDED.description"));
    }

    @Test
    void deleteValue_returnsUpdateCount() {
        when(jdbcTemplate.update(anyString(), eq("os_type"), eq("win"))).thenReturn(1);

        int deleted = repository.deleteValue("os_type", "win");

        assertEquals(1, deleted);
        verify(jdbcTemplate).update(
                eq("DELETE FROM lkp_master WHERE lookup_type = ? AND code = ?"),
                eq("os_type"),
                eq("win")
        );
    }
}

