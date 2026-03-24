package com.e24online.mdm.records.policy;

public record StarterFixSpec(String codeStem,
                             String title,
                             String description,
                             String remediationType,
                             String osType,
                             String deviceType,
                             String instructionJson,
                             short priority) {
}
