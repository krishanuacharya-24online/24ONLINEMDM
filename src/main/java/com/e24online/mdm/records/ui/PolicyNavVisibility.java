package com.e24online.mdm.records.ui;

public record PolicyNavVisibility(
        String shellHref,
        boolean simpleCenter,
        boolean advancedPages,
        boolean auditTrail
) {
}
