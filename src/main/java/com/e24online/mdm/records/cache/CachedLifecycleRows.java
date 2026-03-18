package com.e24online.mdm.records.cache;

import com.e24online.mdm.domain.OsReleaseLifecycleMaster;

import java.util.List;

public record CachedLifecycleRows(long expiresAtEpochMillis, List<OsReleaseLifecycleMaster> rows) {
}
