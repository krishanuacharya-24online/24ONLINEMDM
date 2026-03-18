package com.e24online.mdm.records;

import com.e24online.mdm.domain.OsReleaseLifecycleMaster;

import java.util.List;

public record CachedLifecycleRows(long expiresAtEpochMillis, List<OsReleaseLifecycleMaster> rows) {
}
