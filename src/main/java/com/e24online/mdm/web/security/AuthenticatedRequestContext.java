package com.e24online.mdm.web.security;

import com.e24online.mdm.domain.Tenant;
import com.e24online.mdm.records.user.UserPrincipal;
import com.e24online.mdm.repository.TenantRepository;
import com.e24online.mdm.service.BlockingDb;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.Locale;

@Component
public class AuthenticatedRequestContext {

    private final TenantRepository tenantRepository;
    private final BlockingDb blockingDb;

    public AuthenticatedRequestContext(TenantRepository tenantRepository, BlockingDb blockingDb) {
        this.tenantRepository = tenantRepository;
        this.blockingDb = blockingDb;
    }

    public String resolveActor(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal up) {
            String username = up.username();
            if (username != null && !username.isBlank()) {
                return username.trim();
            }
        }
        return "system";
    }

    public UserPrincipal requireUserPrincipal(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal up)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return up;
    }

    public Mono<String> resolveTenantId(Authentication authentication, String requestedTenantId) {
        return blockingDb.mono(() -> resolveTenantIdBlocking(authentication, requestedTenantId));
    }

    public Mono<String> resolveOptionalTenantId(Authentication authentication, String requestedTenantId) {
        return blockingDb.mono(() -> resolveOptionalTenantIdBlocking(authentication, requestedTenantId));
    }

    public String resolveRole(Authentication authentication) {
        return requireUserPrincipal(authentication).role();
    }

    private String resolveTenantIdBlocking(Authentication authentication, String requestedTenantId) {
        UserPrincipal principal = requireUserPrincipal(authentication);
        String normalizedRequestedTenantId = normalizeOptionalTenantId(requestedTenantId);

        if ("PRODUCT_ADMIN".equalsIgnoreCase(principal.role())) {
            if (normalizedRequestedTenantId == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing X-Tenant-Id");
            }
            return normalizedRequestedTenantId;
        }

        if (!"TENANT_USER".equalsIgnoreCase(principal.role())
                && !"TENANT_ADMIN".equalsIgnoreCase(principal.role())
                && !"AUDITOR".equalsIgnoreCase(principal.role())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Unsupported user role");
        }

        Long principalTenantPk = principal.tenantId();
        if (principalTenantPk == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tenant scope missing for authenticated user");
        }

        Tenant tenant = tenantRepository.findById(principalTenantPk).orElse(null);
        if (tenant == null || tenant.isDeleted() || !"ACTIVE".equalsIgnoreCase(tenant.getStatus())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Authenticated tenant scope is not active");
        }

        String effectiveTenantId = normalizeRequiredTenantId(tenant.getTenantId());
        if (normalizedRequestedTenantId != null && !effectiveTenantId.equals(normalizedRequestedTenantId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tenant scope mismatch");
        }
        return effectiveTenantId;
    }

    private String resolveOptionalTenantIdBlocking(Authentication authentication, String requestedTenantId) {
        UserPrincipal principal = requireUserPrincipal(authentication);
        String normalizedRequestedTenantId = normalizeOptionalTenantId(requestedTenantId);

        if ("PRODUCT_ADMIN".equalsIgnoreCase(principal.role())) {
            return normalizedRequestedTenantId;
        }

        if (!"TENANT_USER".equalsIgnoreCase(principal.role())
                && !"TENANT_ADMIN".equalsIgnoreCase(principal.role())
                && !"AUDITOR".equalsIgnoreCase(principal.role())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Unsupported user role");
        }

        Long principalTenantPk = principal.tenantId();
        if (principalTenantPk == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tenant scope missing for authenticated user");
        }

        Tenant tenant = tenantRepository.findById(principalTenantPk).orElse(null);
        if (tenant == null || tenant.isDeleted() || !"ACTIVE".equalsIgnoreCase(tenant.getStatus())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Authenticated tenant scope is not active");
        }

        String effectiveTenantId = normalizeRequiredTenantId(tenant.getTenantId());
        if (normalizedRequestedTenantId != null && !effectiveTenantId.equals(normalizedRequestedTenantId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tenant scope mismatch");
        }
        return effectiveTenantId;
    }

    private String normalizeOptionalTenantId(String tenantId) {
        if (tenantId == null) {
            return null;
        }
        String normalized = tenantId.trim().toLowerCase(Locale.ROOT);
        return normalized.isBlank() ? null : normalized;
    }

    private String normalizeRequiredTenantId(String tenantId) {
        String normalized = normalizeOptionalTenantId(tenantId);
        if (normalized == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tenant scope is invalid");
        }
        return normalized;
    }
}
