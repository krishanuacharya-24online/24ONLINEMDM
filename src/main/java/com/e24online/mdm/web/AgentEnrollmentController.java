package com.e24online.mdm.web;

import com.e24online.mdm.records.QrClaimRequest;
import com.e24online.mdm.records.SetupKeyClaimRequest;
import com.e24online.mdm.service.DeviceEnrollmentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("${api.version.prefix:v1}")
public class AgentEnrollmentController {

    private final DeviceEnrollmentService enrollmentService;

    public AgentEnrollmentController(DeviceEnrollmentService enrollmentService) {
        this.enrollmentService = enrollmentService;
    }

    @PostMapping("/agent/enrollment/claim/setup-key")
    public Mono<ResponseEntity<DeviceEnrollmentService.AgentEnrollmentClaim>> claimWithSetupKey(
            @Valid @RequestBody Mono<SetupKeyClaimRequest> request
    ) {
        return request.flatMap(body -> enrollmentService.claimWithSetupKeyAsync(
                        body.setupKey(),
                        body.agentId(),
                        body.deviceFingerprint(),
                        body.deviceLabel()
                ))
                .map(claim -> ResponseEntity.status(HttpStatus.CREATED).body(claim));
    }

    @PostMapping("/agent/enrollment/claim/qr")
    public Mono<ResponseEntity<DeviceEnrollmentService.AgentEnrollmentClaim>> claimWithQr(
            @Valid @RequestBody Mono<QrClaimRequest> request
    ) {
        return request.flatMap(body -> enrollmentService.claimWithQrAsync(
                        body.qrToken(),
                        body.agentId(),
                        body.deviceFingerprint(),
                        body.deviceLabel()
                ))
                .map(claim -> ResponseEntity.status(HttpStatus.CREATED).body(claim));
    }

}
