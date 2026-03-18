package com.e24online.mdm.service;

import com.e24online.mdm.records.lookup.LookupRow;
import com.e24online.mdm.repository.LookupJdbcRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class LookupService {

    private final LookupJdbcRepository lookupRepository;
    private final BlockingDb blockingDb;
    private final AuditEventService auditEventService;

    public LookupService(LookupJdbcRepository lookupRepository,
                         BlockingDb blockingDb,
                         AuditEventService auditEventService) {
        this.lookupRepository = lookupRepository;
        this.blockingDb = blockingDb;
        this.auditEventService = auditEventService;
    }

    public Flux<String> listLookupTypes() {
        return blockingDb.flux(() -> {
            java.util.List<String> rows = lookupRepository.listLookupTypes();
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("resultCount", rows.size());
            recordAudit("LOOKUP_TYPES_VIEWED", "LIST", "ui", null, metadata);
            return rows;
        });
    }

    public Flux<LookupRow> listLookupValues(String lookupType) {
        return blockingDb.flux(() -> {
            java.util.List<LookupRow> rows = lookupRepository.listValues(lookupType);
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("lookupType", lookupType);
            metadata.put("resultCount", rows.size());
            recordAudit("LOOKUP_VALUES_VIEWED", "LIST", "ui", lookupType, metadata);
            return rows;
        });
    }

    public Mono<Void> upsertValue(String lookupType, String code, String description) {
        return upsertValue(lookupType, code, description, null);
    }

    public Mono<Void> upsertValue(String lookupType, String code, String description, String actor) {
        return blockingDb.run(() -> {
            lookupRepository.upsertValue(lookupType, code, description);
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("lookupType", lookupType);
            metadata.put("code", code);
            metadata.put("description", description);
            recordAudit("LOOKUP_VALUE_UPSERTED", "UPSERT", actor, code, metadata);
        });
    }

    public Mono<Void> updateValue(String lookupType, String code, String description) {
        return updateValue(lookupType, code, description, null);
    }

    public Mono<Void> updateValue(String lookupType, String code, String description, String actor) {
        return blockingDb.run(() -> {
            lookupRepository.upsertValue(lookupType, code, description);
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("lookupType", lookupType);
            metadata.put("code", code);
            metadata.put("description", description);
            recordAudit("LOOKUP_VALUE_UPDATED", "UPDATE", actor, code, metadata);
        });
    }

    public Mono<Void> deleteValue(String lookupType, String code) {
        return deleteValue(lookupType, code, null);
    }

    public Mono<Void> deleteValue(String lookupType, String code, String actor) {
        return blockingDb.run(() -> {
            int deleted = lookupRepository.deleteValue(lookupType, code);
            if (deleted == 0) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            }
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("lookupType", lookupType);
            metadata.put("code", code);
            recordAudit("LOOKUP_VALUE_DELETED", "DELETE", actor, code, metadata);
        });
    }

    private void recordAudit(String eventType,
                             String action,
                             String actor,
                             String entityId,
                             Map<String, Object> metadata) {
        String effectiveActor = (actor == null || actor.isBlank()) ? "ui" : actor.trim();
        auditEventService.recordBestEffort(
                "LOOKUP_ADMIN",
                eventType,
                action,
                null,
                effectiveActor,
                "LOOKUP_VALUE",
                entityId,
                "SUCCESS",
                metadata
        );
    }
}
