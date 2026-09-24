package co.gamestore.ai;

import tools.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * Google Gemini through its REST API (generateContent).
 */
public class GeminiClient implements AiClient {

    private final AiProperties.Provider config;
    private final RestClient http;

    public GeminiClient(AiProperties.Provider config, RestClient.Builder builder) {
        this.config = config;
        this.http = builder.baseUrl(config.baseUrl()).build();
    }

    @Override
    public String provider() {
        return "gemini";
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
                "systemInstruction", Map.of("parts", List.of(Map.of("text", systemPrompt))),
                "contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", userPrompt)))));
        JsonNode response = http.post()
                .uri("/v1beta/models/{model}:generateContent", config.model())
                .header("x-goog-api-key", config.apiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);
        JsonNode parts = response == null ? null : response.at("/candidates/0/content/parts");
        if (parts == null || !parts.isArray() || parts.isEmpty()) {
            throw new IllegalStateException("Gemini returned no content");
        }
        StringBuilder text = new StringBuilder();
        parts.forEach(p -> text.append(p.path("text").asText("")));
        return text.toString();
    }
}
