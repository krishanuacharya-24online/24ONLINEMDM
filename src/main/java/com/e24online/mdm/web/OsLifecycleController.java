package com.e24online.mdm.web;

import com.e24online.mdm.domain.OsReleaseLifecycleMaster;
import com.e24online.mdm.service.OsLifecycleService;
import com.e24online.mdm.web.security.AuthenticatedRequestContext;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
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
@RequestMapping("${api.version.prefix:v1}/os-lifecycle")
@PreAuthorize("hasRole('PRODUCT_ADMIN')")
public class OsLifecycleController {

    private final OsLifecycleService osLifecycleService;
    private final AuthenticatedRequestContext requestContext;

    public OsLifecycleController(OsLifecycleService osLifecycleService,
                                 AuthenticatedRequestContext requestContext) {
        this.osLifecycleService = osLifecycleService;
        this.requestContext = requestContext;
    }

    @GetMapping
    public Flux<OsReleaseLifecycleMaster> list(
            @RequestParam(name = "platform_code", required = false) String platformCode,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "50") int size
    ) {
        return osLifecycleService.list(platformCode, page, size);
    }

    @GetMapping("/{id}")
    public Mono<OsReleaseLifecycleMaster> get(@PathVariable("id") Long id) {
        return osLifecycleService.get(id);
    }

    @PostMapping
    public Mono<OsReleaseLifecycleMaster> create(
            Authentication authentication,
            @Valid @RequestBody Mono<OsReleaseLifecycleMaster> request
    ) {
        String actor = requestContext.resolveActor(authentication);
        return request.flatMap(body -> osLifecycleService.create(actor, body));
    }

    @PutMapping("/{id}")
    public Mono<OsReleaseLifecycleMaster> update(
            Authentication authentication,
            @PathVariable("id") Long id,
            @Valid @RequestBody Mono<OsReleaseLifecycleMaster> request
    ) {
        String actor = requestContext.resolveActor(authentication);
        return request.flatMap(body -> osLifecycleService.update(actor, id, body));
    }

    @DeleteMapping("/{id}")
    public Mono<Void> delete(
            Authentication authentication,
            @PathVariable("id") Long id
    ) {
        String actor = requestContext.resolveActor(authentication);
        return osLifecycleService.delete(actor, id);
    }
}
