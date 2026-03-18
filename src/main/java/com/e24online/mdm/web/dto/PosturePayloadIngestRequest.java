package com.e24online.mdm.web.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import tools.jackson.databind.JsonNode;

@Setter
@Getter
public class PosturePayloadIngestRequest {

    @NotBlank
    @Size(max = 255)
    @JsonAlias("device_external_id")
    private String deviceExternalId;

    @NotBlank
    @Size(max = 255)
    @JsonAlias("agent_id")
    private String agentId;

    @NotBlank
    @Size(max = 64)
    @JsonAlias("payload_version")
    private String payloadVersion;

    @Size(max = 512)
    @JsonAlias("payload_hash")
    private String payloadHash;

    @NotNull
    @JsonAlias("payload_json")
    private JsonNode payloadJson;

    @Override
    public String toString() {
        return "PosturePayloadIngestRequest{" +
                "deviceExternalId='" + deviceExternalId + '\'' +
                ", agentId='" + agentId + '\'' +
                ", payloadVersion='" + payloadVersion + '\'' +
                ", payloadHash='" + payloadHash + '\'' +
                ", payloadJson=" + payloadJson +
                '}';
    }
}
