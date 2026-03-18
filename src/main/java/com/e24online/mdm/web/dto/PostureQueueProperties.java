package com.e24online.mdm.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "mdm.posture.queue")
public class PostureQueueProperties {

    @NotBlank
    private String exchange = "mdm.posture.eval.exchange";

    @NotBlank
    private String routingKey = "mdm.posture.eval";

    @NotBlank
    private String queue = "mdm.posture.eval.queue";

    @NotBlank
    private String deadLetterExchange = "mdm.posture.eval.dlx";

    @NotBlank
    private String deadLetterRoutingKey = "mdm.posture.eval.dlq";

    @NotBlank
    private String deadLetterQueue = "mdm.posture.eval.dlq";

    @Min(1000)
    private int messageTtlMs = 300_000;

    @Min(1)
    private int consumerConcurrency = 2;

    @Min(1)
    private int maxConsumerConcurrency = 6;

    @Min(1)
    private int retryMaxAttempts = 4;

    @Min(100)
    private long retryInitialIntervalMs = 500L;

    @DecimalMin("1.0")
    private double retryMultiplier = 2.0;

    @Min(100)
    private long retryMaxIntervalMs = 10_000L;
}
