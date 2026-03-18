package com.e24online.mdm.web;

import com.e24online.mdm.service.LookupService;
import com.e24online.mdm.web.dto.LookupEntryDto;
import com.e24online.mdm.web.dto.LookupTypeInfo;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("${api.version.prefix:v1}/lookups")
@PreAuthorize("hasAnyRole('PRODUCT_ADMIN','TENANT_ADMIN')")
public class LookupController {

    private final LookupService lookupService;

    public LookupController(LookupService lookupService) {
        this.lookupService = lookupService;
    }

    @GetMapping
    public Flux<LookupTypeInfo> listLookupTypes() {
        return lookupService.listLookupTypes()
                .map(t -> new LookupTypeInfo(t, ""));
    }

    @GetMapping("/{lookup_type}")
    public Flux<LookupEntryDto> listLookupValues(
            @PathVariable("lookup_type") String lookupType
    ) {
        return lookupService.listLookupValues(lookupType)
                .map(e -> new LookupEntryDto(e.lookupType(), e.code(), e.description()));
    }

}
