package com.shopscale.ai;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * OpenAI through the Chat Completions REST API.
 */
public class OpenAiClient implements AiClient {

    private final AiProperties.Provider config;
    private final RestClient http;

    public OpenAiClient(AiProperties.Provider config, RestClient.Builder builder) {
        this.config = config;
        this.http = builder.baseUrl(config.baseUrl()).build();
    }

    @Override
    public String provider() {
        return "openai";
    }

    @Override
    public String model() {
        return config.model();
    }

    @Override
    public boolean isConfigured() {
        return config.hasKey();
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        Map<String, Object> body = Map.of(
                "model", config.model(),
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)));
        JsonNode response = http.post()
                .uri("/v1/chat/completions")
                .header("Authorization", "Bearer " + config.apiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);
        JsonNode content = response == null ? null : response.at("/choices/0/message/content");
        if (content == null || content.isMissingNode() || content.asText().isBlank()) {
            throw new IllegalStateException("OpenAI returned no content");
        }
        return content.asText();
    }
}
