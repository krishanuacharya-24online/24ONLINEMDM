package com.e24online.mdm.service;

import com.e24online.mdm.domain.OsReleaseLifecycleMaster;
import com.e24online.mdm.repository.OsReleaseLifecycleMasterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.scheduler.Schedulers;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OsLifecycleServiceTest {

    @Mock
    private OsReleaseLifecycleMasterRepository repository;

    private OsLifecycleService service;

    @BeforeEach
    void setUp() {
        service = new OsLifecycleService(repository, new BlockingDb(Schedulers.immediate()));
        lenient().when(repository.save(any(OsReleaseLifecycleMaster.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void list_normalizesPageAndSize() {
        when(repository.findPaged("ANDROID", 50, 0L)).thenReturn(List.of(entry(1L)));
        when(repository.findPaged("ANDROID", 500, 1000L)).thenReturn(List.of(entry(2L)));

        List<OsReleaseLifecycleMaster> first = service.list("ANDROID", -4, 0).collectList().block();
        List<OsReleaseLifecycleMaster> second = service.list("ANDROID", 2, 999).collectList().block();

        assertEquals(1, first.size());
        assertEquals(1, second.size());
        verify(repository, times(1)).findPaged("ANDROID", 50, 0L);
        verify(repository, times(1)).findPaged("ANDROID", 500, 1000L);
    }

    @Test
    void get_returnsEntryOr404() {
        when(repository.findById(10L)).thenReturn(Optional.of(entry(10L)));
        when(repository.findById(99L)).thenReturn(Optional.empty());

        OsReleaseLifecycleMaster found = service.get(10L).block();
        assertEquals(10L, found.getId());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.get(99L).block());
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        assertEquals("OS lifecycle entry not found", ex.getReason());
    }

    @Test
    void create_setsDefaultsAndActorFallback() {
        OsReleaseLifecycleMaster body = new OsReleaseLifecycleMaster();
        body.setId(999L);
        body.setDeleted(true);

        OsReleaseLifecycleMaster created = service.create(null, body).block();

        assertNull(created.getId());
        assertEquals(false, created.isDeleted());
        assertEquals("ui", created.getCreatedBy());
        assertEquals("ui", created.getModifiedBy());
        assertNotNull(created.getCreatedAt());
        assertNotNull(created.getModifiedAt());
    }

    @Test
    void update_preservesCreatedFieldsAndDeletionFlag() {
        OsReleaseLifecycleMaster existing = entry(20L);
        existing.setCreatedBy("seed");
        existing.setCreatedAt(OffsetDateTime.now().minusDays(2));
        existing.setDeleted(true);
        when(repository.findById(20L)).thenReturn(Optional.of(existing));

        OsReleaseLifecycleMaster body = new OsReleaseLifecycleMaster();
        body.setCreatedBy("should-not-win");
        body.setDeleted(false);

        OsReleaseLifecycleMaster updated = service.update("", 20L, body).block();

        assertEquals(20L, updated.getId());
        assertEquals("seed", updated.getCreatedBy());
        assertEquals(existing.getCreatedAt(), updated.getCreatedAt());
        assertEquals(true, updated.isDeleted());
        assertEquals("ui", updated.getModifiedBy());
        assertNotNull(updated.getModifiedAt());
    }

    @Test
    void update_notFoundReturns404() {
        when(repository.findById(404L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                service.update("actor", 404L, new OsReleaseLifecycleMaster()).block()
        );

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void delete_marksDeletedAndUpdatesAudit() {
        OsReleaseLifecycleMaster existing = entry(30L);
        when(repository.findById(30L)).thenReturn(Optional.of(existing));

        service.delete("deleter", 30L).block();

        assertEquals(true, existing.isDeleted());
        assertEquals("deleter", existing.getModifiedBy());
        assertNotNull(existing.getModifiedAt());
        verify(repository, times(1)).save(existing);
    }

    @Test
    void delete_notFoundReturns404() {
        when(repository.findById(405L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                service.delete(null, 405L).block()
        );

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    private OsReleaseLifecycleMaster entry(Long id) {
        OsReleaseLifecycleMaster row = new OsReleaseLifecycleMaster();
        row.setId(id);
        row.setPlatformCode("ANDROID");
        row.setCycle("14");
        row.setStatus("ACTIVE");
        row.setDeleted(false);
        row.setCreatedAt(OffsetDateTime.now().minusDays(1));
        row.setCreatedBy("seed");
        return row;
    }
}

