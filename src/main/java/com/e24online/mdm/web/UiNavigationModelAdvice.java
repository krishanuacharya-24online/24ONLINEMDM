package com.e24online.mdm.web;

import com.e24online.mdm.records.ui.NavVisibility;
import com.e24online.mdm.records.ui.PolicyNavVisibility;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.HashSet;
import java.util.Set;

@ControllerAdvice(annotations = Controller.class)
public class UiNavigationModelAdvice {

    @ModelAttribute("nav")
    public NavVisibility navVisibility(Authentication authentication) {
        Set<String> roles = extractRoles(authentication);

        boolean productAdmin = roles.contains("PRODUCT_ADMIN");
        boolean tenantAdmin = roles.contains("TENANT_ADMIN");
        boolean tenantUser = roles.contains("TENANT_USER");
        boolean auditor = roles.contains("AUDITOR");

        return new NavVisibility(
                productAdmin || tenantAdmin,                 // overview
                productAdmin || tenantAdmin || tenantUser,   // devices
                tenantAdmin || tenantUser,                   // enrollments
                productAdmin,                                // payloads
                productAdmin || tenantAdmin || auditor,      // audit
                productAdmin || tenantAdmin || auditor,      // policies
                productAdmin,                                // catalog
                productAdmin,                                // lookups
                productAdmin,                                // tenants
                productAdmin || tenantAdmin,                 // users
                productAdmin || tenantAdmin,                 // reports
                productAdmin,                                // osLifecycle
                productAdmin                                 // management
        );
    }

    @ModelAttribute("policyNav")
    public PolicyNavVisibility policyNavVisibility(Authentication authentication) {
        Set<String> roles = extractRoles(authentication);

        boolean productAdmin = roles.contains("PRODUCT_ADMIN");
        boolean tenantAdmin = roles.contains("TENANT_ADMIN");
        boolean auditor = roles.contains("AUDITOR");

        String shellHref = auditor && !productAdmin && !tenantAdmin
                ? "/ui/policies/audit-trail"
                : "/ui/policies";

        return new PolicyNavVisibility(
                shellHref,
                productAdmin || tenantAdmin,
                productAdmin,
                productAdmin || auditor
        );
    }

    private Set<String> extractRoles(Authentication authentication) {
        Set<String> roles = new HashSet<>();
        if (authentication == null || authentication.getAuthorities() == null) {
            return roles;
        }
        authentication.getAuthorities().forEach(authority -> {
            String value = authority == null ? "" : String.valueOf(authority.getAuthority());
            String normalized = normalizeRole(value);
            if (!normalized.isBlank()) {
                roles.add(normalized);
            }
        });
        return roles;
    }

    private String normalizeRole(String role) {
        String normalized = role == null ? "" : role.trim().toUpperCase();
        if (normalized.startsWith("ROLE_")) {
            return normalized.substring(5);
        }
        return normalized;
    }

}
