package com.e24online.mdm.web;

import com.e24online.mdm.domain.ApplicationCatalogEntry;
import com.e24online.mdm.service.CatalogService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("${api.version.prefix:v1}/catalog")
@PreAuthorize("hasAnyRole('PRODUCT_ADMIN','TENANT_ADMIN')")
public class CatalogController {

    private final CatalogService catalogService;

    public CatalogController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping("/applications")
    public Flux<ApplicationCatalogEntry> listApplications(
            @RequestParam(name = "os_type", required = false) String osType,
            @RequestParam(name = "search", required = false) String search,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "50") int size
    ) {
        return catalogService.listApplications(osType, search, page, size);
    }

    @GetMapping("/applications/{application_catalog_id}")
    public Mono<ApplicationCatalogEntry> getApplication(
            @PathVariable("application_catalog_id") Long id
    ) {
        return catalogService.getApplication(id);
    }

    @PostMapping("/applications")
    @PreAuthorize("hasRole('PRODUCT_ADMIN')")
    public Mono<ApplicationCatalogEntry> createApplication(
            @Valid @RequestBody Mono<ApplicationCatalogEntry> request
    ) {
        return request.flatMap(catalogService::createApplication);
    }

    @PutMapping("/applications/{application_catalog_id}")
    @PreAuthorize("hasRole('PRODUCT_ADMIN')")
    public Mono<ApplicationCatalogEntry> updateApplication(
            @PathVariable("application_catalog_id") Long id,
            @Valid @RequestBody Mono<ApplicationCatalogEntry> request
    ) {
        return request.flatMap(body -> catalogService.updateApplication(id, body));
    }

    @DeleteMapping("/applications/{application_catalog_id}")
    @PreAuthorize("hasRole('PRODUCT_ADMIN')")
    public Mono<Void> deleteApplication(
            @PathVariable("application_catalog_id") Long id
    ) {
        return catalogService.deleteApplication(id);
    }
}
