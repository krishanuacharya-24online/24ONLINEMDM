package com.e24online.mdm.service;

import com.e24online.mdm.domain.OsReleaseLifecycleMaster;
import com.e24online.mdm.repository.OsReleaseLifecycleMasterRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;

@Service
public class OsLifecycleService {
    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int MAX_PAGE_SIZE = 500;

    private final OsReleaseLifecycleMasterRepository repository;
    private final BlockingDb blockingDb;

    public OsLifecycleService(OsReleaseLifecycleMasterRepository repository, BlockingDb blockingDb) {
        this.repository = repository;
        this.blockingDb = blockingDb;
    }

    public Flux<OsReleaseLifecycleMaster> list(String platformCode, int page, int size) {
        int limit = normalizePageSize(size);
        long offset = (long) normalizePage(page) * limit;
        return blockingDb.flux(() -> repository.findPaged(platformCode, limit, offset));
    }

    public Mono<OsReleaseLifecycleMaster> get(Long id) {
        return blockingDb.mono(() -> repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "OS lifecycle entry not found")));
    }

    public Mono<OsReleaseLifecycleMaster> create(String actor, OsReleaseLifecycleMaster body) {
        String a = (actor == null || actor.isBlank()) ? "ui" : actor;
        return blockingDb.mono(() -> {
            OffsetDateTime now = OffsetDateTime.now();
            body.setId(null);
            body.setDeleted(false);
            body.setCreatedAt(now);
            body.setCreatedBy(a);
            body.setModifiedAt(now);
            body.setModifiedBy(a);
            return repository.save(body);
        });
    }

    public Mono<OsReleaseLifecycleMaster> update(String actor, Long id, OsReleaseLifecycleMaster body) {
        String a = (actor == null || actor.isBlank()) ? "ui" : actor;
        return blockingDb.mono(() -> {
            OsReleaseLifecycleMaster existing = repository.findById(id)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
            OffsetDateTime now = OffsetDateTime.now();
            body.setId(id);
            body.setCreatedAt(existing.getCreatedAt());
            body.setCreatedBy(existing.getCreatedBy());
            body.setDeleted(existing.isDeleted());
            body.setModifiedAt(now);
            body.setModifiedBy(a);
            return repository.save(body);
        });
    }

    public Mono<Void> delete(String actor, Long id) {
        String a = (actor == null || actor.isBlank()) ? "ui" : actor;
        return blockingDb.run(() -> {
            OsReleaseLifecycleMaster existing = repository.findById(id)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
            existing.setDeleted(true);
            existing.setModifiedAt(OffsetDateTime.now());
            existing.setModifiedBy(a);
            repository.save(existing);
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
