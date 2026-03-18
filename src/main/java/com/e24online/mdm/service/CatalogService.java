package com.e24online.mdm.service;

import com.e24online.mdm.domain.ApplicationCatalogEntry;
import com.e24online.mdm.repository.ApplicationCatalogRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;

@Service
public class CatalogService {
    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int MAX_PAGE_SIZE = 500;

    private final ApplicationCatalogRepository applicationCatalogRepository;
    private final BlockingDb blockingDb;

    public CatalogService(ApplicationCatalogRepository applicationCatalogRepository, BlockingDb blockingDb) {
        this.applicationCatalogRepository = applicationCatalogRepository;
        this.blockingDb = blockingDb;
    }

    public Flux<ApplicationCatalogEntry> listApplications(String osType, String search, int page, int size) {
        int limit = normalizePageSize(size);
        long offset = (long) normalizePage(page) * limit;
        return blockingDb.flux(() -> applicationCatalogRepository.findPaged(osType, search, limit, offset));
    }

    public Mono<ApplicationCatalogEntry> getApplication(Long id) {
        return blockingDb.mono(() -> applicationCatalogRepository.findActiveById(id)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found")));
    }

    public Mono<ApplicationCatalogEntry> createApplication(ApplicationCatalogEntry body) {
        return blockingDb.mono(() -> {
            OffsetDateTime now = OffsetDateTime.now();
            body.setId(null);
            body.setDeleted(false);
            body.setCreatedAt(now);
            body.setModifiedAt(now);
            return applicationCatalogRepository.save(body);
        });
    }

    public Mono<ApplicationCatalogEntry> updateApplication(Long id, ApplicationCatalogEntry body) {
        return blockingDb.mono(() -> {
            ApplicationCatalogEntry existing = applicationCatalogRepository.findActiveById(id)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
            body.setId(id);
            body.setCreatedAt(existing.getCreatedAt());
            body.setDeleted(existing.isDeleted());
            body.setModifiedAt(OffsetDateTime.now());
            return applicationCatalogRepository.save(body);
        });
    }

    public Mono<Void> deleteApplication(Long id) {
        return blockingDb.run(() -> {
            ApplicationCatalogEntry existing = applicationCatalogRepository.findActiveById(id)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found"));
            existing.setDeleted(true);
            existing.setModifiedAt(OffsetDateTime.now());
            applicationCatalogRepository.save(existing);
        });
    }

    private int normalizePageSize(int size) {
        if (size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private int normalizePage(int page) {
        return Math.max(page, 0);
    }
}
