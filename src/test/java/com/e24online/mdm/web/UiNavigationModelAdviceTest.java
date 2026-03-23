package com.e24online.mdm.web;

import com.e24online.mdm.records.ui.NavVisibility;
import com.e24online.mdm.records.ui.PolicyNavVisibility;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiNavigationModelAdviceTest {

    private final UiNavigationModelAdvice advice = new UiNavigationModelAdvice();

    @Test
    void productAdminGetsFullPolicyNavigation() {
        Authentication authentication = authentication("ROLE_PRODUCT_ADMIN");

        NavVisibility nav = advice.navVisibility(authentication);
        PolicyNavVisibility policyNav = advice.policyNavVisibility(authentication);

        assertTrue(nav.policies());
        assertEquals("/ui/policies", policyNav.shellHref());
        assertTrue(policyNav.simpleCenter());
        assertTrue(policyNav.advancedPages());
        assertTrue(policyNav.auditTrail());
    }

    @Test
    void tenantAdminGetsSimplePolicyNavigationOnly() {
        Authentication authentication = authentication("ROLE_TENANT_ADMIN");

        NavVisibility nav = advice.navVisibility(authentication);
        PolicyNavVisibility policyNav = advice.policyNavVisibility(authentication);

        assertTrue(nav.policies());
        assertEquals("/ui/policies", policyNav.shellHref());
        assertTrue(policyNav.simpleCenter());
        assertFalse(policyNav.advancedPages());
        assertFalse(policyNav.auditTrail());
    }

    @Test
    void auditorGetsPolicyAuditNavigation() {
        Authentication authentication = authentication("ROLE_AUDITOR");

        NavVisibility nav = advice.navVisibility(authentication);
        PolicyNavVisibility policyNav = advice.policyNavVisibility(authentication);

        assertTrue(nav.policies());
        assertEquals("/ui/policies/audit-trail", policyNav.shellHref());
        assertFalse(policyNav.simpleCenter());
        assertFalse(policyNav.advancedPages());
        assertTrue(policyNav.auditTrail());
    }

    private Authentication authentication(String... roles) {
        return new UsernamePasswordAuthenticationToken(
                "user",
                "n/a",
                List.of(roles).stream().map(SimpleGrantedAuthority::new).toList()
        );
    }
}
