package com.e24online.mdm.records;

public record CacheEntry<T>(long expiresAtEpochMillis, T value) {
}
