package com.e24online.mdm.web;

import com.e24online.mdm.records.ui.NavVisibility;
import com.e24online.mdm.records.ui.PolicyNavVisibility;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.Collections;
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
        if (authentication == null) {
            return Collections.emptySet();
        }
        var authorities = authentication.getAuthorities();
        Set<String> roles = HashSet.newHashSet(authorities.size());

        for (var authority : authorities) {
            if (authority != null) {
                String role = normalizeRole(authority.getAuthority());
                if (!role.isBlank()) {
                    roles.add(role);
                }
            }
        }
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
