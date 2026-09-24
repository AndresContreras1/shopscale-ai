package co.gamestore.ai;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ai")
public record AiProperties(String provider, Duration timeout, Provider gemini, Provider openai) {

    public record Provider(String apiKey, String model, String baseUrl) {

        public boolean hasKey() {
            return apiKey != null && !apiKey.isBlank();
        }
    }
}
