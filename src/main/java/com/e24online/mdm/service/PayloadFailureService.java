package com.e24online.mdm.service;

import com.e24online.mdm.domain.DevicePosturePayload;
import com.e24online.mdm.repository.DevicePosturePayloadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Service
@RequiredArgsConstructor
public class PayloadFailureService {

    private final DevicePosturePayloadRepository payloadRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markPayloadFailed(DevicePosturePayload payload, String errorMessage, int maxProcessErrorLength) {
        payload.setProcessStatus("FAILED");
        payload.setProcessedAt(OffsetDateTime.now(ZoneOffset.UTC));
        payload.setProcessError(truncate(errorMessage, maxProcessErrorLength));
        payloadRepository.save(payload);
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}