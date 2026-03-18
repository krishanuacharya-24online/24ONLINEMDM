package com.e24online.mdm.records.tenant;

/**
 * Holds resolved tenant context for a request.
 */
public record TenantContext(String tenantId, Long ownerUserId) {
}