package com.e24online.mdm.records.ui;

/**
 * Holds pagination parameters after normalization.
 */
public record PageParams(int limit, long offset) {
    public static PageParams of(int page, int size, int defaultSize, int maxSize, int maxPage) {
        int normalizedPage = Math.clamp(page, 0, maxPage);
        int normalizedSize = size <= 0 ? defaultSize : Math.min(size, maxSize);
        return new PageParams(normalizedSize, (long) normalizedPage * normalizedSize);
    }
}