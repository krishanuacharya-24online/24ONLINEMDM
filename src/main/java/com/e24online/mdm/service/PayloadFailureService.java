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
        OffsetDateTime processedAt = OffsetDateTime.now(ZoneOffset.UTC);
        String processError = truncate(errorMessage, maxProcessErrorLength);
        payload.setProcessStatus("FAILED");
        payload.setProcessedAt(processedAt);
        payload.setProcessError(processError);

        if (payload.getId() == null) {
            payloadRepository.save(payload);
            return;
        }

        payloadRepository.markPayloadFailed(payload.getId(), processError, processedAt);
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
