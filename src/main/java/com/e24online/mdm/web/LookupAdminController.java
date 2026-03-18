package com.e24online.mdm.web;

import com.e24online.mdm.records.lookup.LookupUpdateRequest;
import com.e24online.mdm.records.lookup.LookupUpsertRequest;
import com.e24online.mdm.service.LookupService;
import com.e24online.mdm.web.security.AuthenticatedRequestContext;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("${api.version.prefix:v1}/admin/lookups")
@PreAuthorize("hasRole('PRODUCT_ADMIN')")
public class LookupAdminController {

    private final LookupService lookupService;
    private final AuthenticatedRequestContext requestContext;

    public LookupAdminController(LookupService lookupService,
                                 AuthenticatedRequestContext requestContext) {
        this.lookupService = lookupService;
        this.requestContext = requestContext;
    }

    @PostMapping("/{lookup_type}")
    public Mono<Void> createOrUpdateLookupValue(
            Authentication authentication,
            @PathVariable("lookup_type") String lookupType,
            @Valid @RequestBody Mono<LookupUpsertRequest> request
    ) {
        String actor = requestContext.resolveActor(authentication);
        return request.flatMap(r -> lookupService.upsertValue(lookupType, r.code(), r.description(), actor));
    }

    @PutMapping("/{lookup_type}/{code}")
    public Mono<Void> updateLookupValue(
            Authentication authentication,
            @PathVariable("lookup_type") String lookupType,
            @PathVariable("code") String code,
            @Valid @RequestBody Mono<LookupUpdateRequest> request
    ) {
        String actor = requestContext.resolveActor(authentication);
        return request.flatMap(r -> lookupService.updateValue(lookupType, code, r.description(), actor));
    }

    @DeleteMapping("/{lookup_type}/{code}")
    public Mono<Void> deleteLookupValue(
            Authentication authentication,
            @PathVariable("lookup_type") String lookupType,
            @PathVariable("code") String code
    ) {
        String actor = requestContext.resolveActor(authentication);
        return lookupService.deleteValue(lookupType, code, actor);
    }

}
