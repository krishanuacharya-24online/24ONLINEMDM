package com.e24online.mdm.service;

import com.e24online.mdm.domain.ApplicationCatalogEntry;
import com.e24online.mdm.repository.ApplicationCatalogRepository;
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
class CatalogServiceTest {

    @Mock
    private ApplicationCatalogRepository applicationCatalogRepository;

    private CatalogService service;

    @BeforeEach
    void setUp() {
        service = new CatalogService(applicationCatalogRepository, new BlockingDb(Schedulers.immediate()));
        lenient().when(applicationCatalogRepository.save(any(ApplicationCatalogEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void listApplications_normalizesPageAndSize() {
        when(applicationCatalogRepository.findPaged("ANDROID", "bad", 50, 0L)).thenReturn(List.of(entry(1L)));
        when(applicationCatalogRepository.findPaged("ANDROID", "bad", 500, 1000L)).thenReturn(List.of(entry(2L)));

        List<ApplicationCatalogEntry> first = service.listApplications("ANDROID", "bad", -3, 0).collectList().block();
        List<ApplicationCatalogEntry> second = service.listApplications("ANDROID", "bad", 2, 999).collectList().block();

        assertEquals(1, first.size());
        assertEquals(1, second.size());
        verify(applicationCatalogRepository, times(1)).findPaged("ANDROID", "bad", 50, 0L);
        verify(applicationCatalogRepository, times(1)).findPaged("ANDROID", "bad", 500, 1000L);
    }

    @Test
    void getApplication_returnsEntryOr404() {
        when(applicationCatalogRepository.findActiveById(10L)).thenReturn(Optional.of(entry(10L)));
        when(applicationCatalogRepository.findActiveById(404L)).thenReturn(Optional.empty());

        ApplicationCatalogEntry found = service.getApplication(10L).block();
        assertEquals(10L, found.getId());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.getApplication(404L).block());
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        assertEquals("Application not found", ex.getReason());
    }

    @Test
    void createApplication_setsDefaults() {
        ApplicationCatalogEntry body = new ApplicationCatalogEntry();
        body.setId(999L);
        body.setDeleted(true);
        body.setCreatedAt(OffsetDateTime.now().minusDays(1));

        ApplicationCatalogEntry created = service.createApplication(body).block();

        assertNull(created.getId());
        assertEquals(false, created.isDeleted());
        assertNotNull(created.getCreatedAt());
        assertNotNull(created.getModifiedAt());
    }

    @Test
    void updateApplication_preservesCreatedAtAndDeletionFlag() {
        ApplicationCatalogEntry existing = entry(20L);
        existing.setCreatedAt(OffsetDateTime.now().minusDays(3));
        existing.setDeleted(true);
        when(applicationCatalogRepository.findActiveById(20L)).thenReturn(Optional.of(existing));

        ApplicationCatalogEntry body = new ApplicationCatalogEntry();
        body.setCreatedAt(OffsetDateTime.now());
        body.setDeleted(false);
        body.setAppName("Updated");

        ApplicationCatalogEntry updated = service.updateApplication(20L, body).block();

        assertEquals(20L, updated.getId());
        assertEquals(existing.getCreatedAt(), updated.getCreatedAt());
        assertEquals(true, updated.isDeleted());
        assertNotNull(updated.getModifiedAt());
    }

    @Test
    void updateApplication_notFoundReturns404() {
        when(applicationCatalogRepository.findActiveById(405L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                service.updateApplication(405L, new ApplicationCatalogEntry()).block()
        );

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void deleteApplication_marksDeletedAndUpdatesModifiedTime() {
        ApplicationCatalogEntry existing = entry(30L);
        when(applicationCatalogRepository.findActiveById(30L)).thenReturn(Optional.of(existing));

        service.deleteApplication(30L).block();

        assertEquals(true, existing.isDeleted());
        assertNotNull(existing.getModifiedAt());
        verify(applicationCatalogRepository, times(1)).save(existing);
    }

    @Test
    void deleteApplication_notFoundReturns404WithReason() {
        when(applicationCatalogRepository.findActiveById(406L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                service.deleteApplication(406L).block()
        );

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        assertEquals("Application not found", ex.getReason());
    }

    private ApplicationCatalogEntry entry(Long id) {
        ApplicationCatalogEntry entry = new ApplicationCatalogEntry();
        entry.setId(id);
        entry.setOsType("ANDROID");
        entry.setPackageId("com.test.app");
        entry.setAppName("Test App");
        entry.setPublisher("Acme");
        entry.setCreatedAt(OffsetDateTime.now().minusDays(1));
        entry.setModifiedAt(OffsetDateTime.now().minusHours(1));
        entry.setDeleted(false);
        return entry;
    }
}

