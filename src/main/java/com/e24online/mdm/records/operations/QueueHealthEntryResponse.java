package com.e24online.mdm.records.operations;

public record QueueHealthEntryResponse(
        String pipelineKey,
        String queueName,
        String deadLetterQueueName,
        long readyMessages,
        long deadLetterMessages,
        long activeConsumers,
        int configuredConsumers,
        int maxConsumers,
        String status,
        String errorMessage
) {
}
