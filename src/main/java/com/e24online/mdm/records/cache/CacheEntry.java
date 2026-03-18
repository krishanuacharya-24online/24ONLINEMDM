package com.e24online.mdm.records.cache;

public record CacheEntry<T>(long expiresAtEpochMillis, T value) {
}
