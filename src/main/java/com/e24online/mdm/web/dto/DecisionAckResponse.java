package com.e24online.mdm.web.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class DecisionAckResponse {

    private Long responseId;
    private String deliveryStatus;
    private OffsetDateTime acknowledgedAt;

}
