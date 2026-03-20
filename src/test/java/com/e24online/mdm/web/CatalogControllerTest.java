package com.e24online.mdm.web;

import com.e24online.mdm.domain.ApplicationCatalogEntry;
import com.e24online.mdm.service.CatalogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CatalogControllerTest {

    @Mock
    private CatalogService catalogService;

    private CatalogController controller;

    @BeforeEach
    void setUp() {
        controller = new CatalogController(catalogService);
    }

    @Test
    void listApplications_prefersCanonicalOsTypeParameter() {
        when(catalogService.listApplications("WINDOWS", "chrome", 1, 25))
                .thenReturn(Flux.just(entry(1L)));

        List<ApplicationCatalogEntry> rows = controller
                .listApplications("WINDOWS", "ANDROID", "chrome", 1, 25)
                .collectList()
                .block();

        assertNotNull(rows);
        assertEquals(1, rows.size());
        verify(catalogService).listApplications("WINDOWS", "chrome", 1, 25);
    }

    @Test
    void listApplications_acceptsLegacyCamelCaseOsTypeParameter() {
        when(catalogService.listApplications("MACOS", null, 0, 50))
                .thenReturn(Flux.just(entry(2L)));

        List<ApplicationCatalogEntry> rows = controller
                .listApplications(null, "MACOS", null, 0, 50)
                .collectList()
                .block();

        assertNotNull(rows);
        assertEquals(1, rows.size());
        verify(catalogService).listApplications("MACOS", null, 0, 50);
    }

    private ApplicationCatalogEntry entry(Long id) {
        ApplicationCatalogEntry entry = new ApplicationCatalogEntry();
        entry.setId(id);
        entry.setOsType("WINDOWS");
        entry.setAppName("Test App");
        return entry;
    }
}
