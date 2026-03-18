package com.e24online.mdm.service;

import com.e24online.mdm.records.lookup.LookupRow;
import com.e24online.mdm.repository.LookupJdbcRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.scheduler.Schedulers;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LookupServiceTest {

    @Mock
    private LookupJdbcRepository lookupRepository;

    @Mock
    private AuditEventService auditEventService;

    private LookupService service;

    @BeforeEach
    void setUp() {
        service = new LookupService(lookupRepository, new BlockingDb(Schedulers.immediate()), auditEventService);
    }

    @Test
    void listLookupTypes_returnsRowsFromRepository() {
        when(lookupRepository.listLookupTypes()).thenReturn(List.of("OS_TYPE", "ROLE"));

        List<String> types = service.listLookupTypes().collectList().block();

        assertEquals(List.of("OS_TYPE", "ROLE"), types);
        verify(lookupRepository, times(1)).listLookupTypes();
    }

    @Test
    void listLookupValues_returnsRowsFromRepository() {
        LookupRow one = new LookupRow("OS_TYPE", "ANDROID", "Android");
        LookupRow two = new LookupRow("OS_TYPE", "IOS", "iOS");
        when(lookupRepository.listValues("OS_TYPE")).thenReturn(List.of(one, two));

        List<LookupRow> rows = service.listLookupValues("OS_TYPE").collectList().block();

        assertEquals(2, rows.size());
        assertEquals("ANDROID", rows.get(0).code());
        assertEquals("IOS", rows.get(1).code());
        verify(lookupRepository, times(1)).listValues("OS_TYPE");
    }

    @Test
    void upsertValue_delegatesToRepository() {
        service.upsertValue("STATUS", "ACTIVE", "Active status").block();
        verify(lookupRepository, times(1)).upsertValue("STATUS", "ACTIVE", "Active status");
    }

    @Test
    void updateValue_routesToUpsertRepositoryCall() {
        service.updateValue("STATUS", "INACTIVE", "Inactive status").block();
        verify(lookupRepository, times(1)).upsertValue("STATUS", "INACTIVE", "Inactive status");
    }

    @Test
    void deleteValue_whenRowExists_completes() {
        when(lookupRepository.deleteValue("STATUS", "ACTIVE")).thenReturn(1);

        service.deleteValue("STATUS", "ACTIVE").block();

        verify(lookupRepository, times(1)).deleteValue("STATUS", "ACTIVE");
    }

    @Test
    void deleteValue_whenNoRowsDeleted_returns404() {
        when(lookupRepository.deleteValue("STATUS", "MISSING")).thenReturn(0);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                service.deleteValue("STATUS", "MISSING").block()
        );

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        verify(lookupRepository, times(1)).deleteValue("STATUS", "MISSING");
        verify(lookupRepository, never()).upsertValue("STATUS", "MISSING", "unused");
    }
}
