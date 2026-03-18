package com.e24online.mdm.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class CreateEvaluationRunRequest {

    @NotNull
    @Positive
    private Long payloadId;

    private boolean forceRecalculate;

    @NotBlank
    private String requestedBy;

}

