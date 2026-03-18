package com.e24online.mdm.records;

import com.e24online.mdm.domain.DevicePosturePayload;

public record IngestionResult(DevicePosturePayload payload, boolean createdNew) {
}
