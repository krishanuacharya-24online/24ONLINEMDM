package com.e24online.mdm.records.ui;

public record NavVisibility(
        boolean overview,
        boolean devices,
        boolean enrollments,
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
