package com.e24online.mdm.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Setter
@Getter
public class DecisionAckRequest {

    @NotBlank
    @Size(max = 32)
    private String deliveryStatus;

    private OffsetDateTime acknowledgedAt;

    @Size(max = 2000)
    private String errorMessage;

}
