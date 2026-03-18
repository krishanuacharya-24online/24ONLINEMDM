package com.e24online.mdm.records;

public record NavVisibility(
        boolean overview,
        boolean devices,
        boolean enrollments,
        boolean payloads,
        boolean audit,
        boolean policies,
        boolean catalog,
        boolean lookups,
        boolean tenants,
        boolean users,
        boolean reports,
        boolean osLifecycle,
        boolean management
) {
}
