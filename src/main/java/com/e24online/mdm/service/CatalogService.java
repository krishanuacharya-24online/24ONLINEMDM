package com.e24online.mdm.service;

import com.e24online.mdm.domain.ApplicationCatalogEntry;
import com.e24online.mdm.repository.ApplicationCatalogRepository;
import com.e24online.mdm.utils.TextSanitizer;
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
        String normalizedOsType = normalizeOptionalText(osType);
        if (normalizedOsType != null) {
            normalizedOsType = normalizedOsType.toUpperCase();
        }
        String normalizedSearch = normalizeOptionalText(search);
        String finalNormalizedOsType = normalizedOsType;
        String finalNormalizedSearch = normalizedSearch;
        return blockingDb.flux(() -> applicationCatalogRepository.findPaged(finalNormalizedOsType, finalNormalizedSearch, limit, offset))
                .map(this::sanitizeEntryForOutput);
    }

    public Mono<ApplicationCatalogEntry> getApplication(Long id) {
        return blockingDb.mono(() -> applicationCatalogRepository.findActiveById(id)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found")))
                .map(this::sanitizeEntryForOutput);
    }

    public Mono<ApplicationCatalogEntry> createApplication(ApplicationCatalogEntry body) {
        return blockingDb.mono(() -> {
            OffsetDateTime now = OffsetDateTime.now();
            body.setId(null);
            body.setDeleted(false);
            body.setCreatedAt(now);
            body.setModifiedAt(now);
            return sanitizeEntryForOutput(applicationCatalogRepository.save(sanitizeEntryForPersistence(body)));
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
            return sanitizeEntryForOutput(applicationCatalogRepository.save(sanitizeEntryForPersistence(body)));
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

    private String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private ApplicationCatalogEntry sanitizeEntryForPersistence(ApplicationCatalogEntry entry) {
        if (entry == null) {
            return null;
        }
        entry.setOsType(TextSanitizer.sanitizeText(entry.getOsType()));
        entry.setPackageId(TextSanitizer.sanitizeText(entry.getPackageId()));
        entry.setPublisher(TextSanitizer.sanitizeText(entry.getPublisher()));
        entry.setAppName(TextSanitizer.sanitizeAppDisplayName(entry.getAppName(), entry.getPackageId()));
        return entry;
    }

    private ApplicationCatalogEntry sanitizeEntryForOutput(ApplicationCatalogEntry entry) {
        return sanitizeEntryForPersistence(entry);
    }
}
