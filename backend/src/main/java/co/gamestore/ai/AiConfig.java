package co.gamestore.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class AiConfig {

    private static final Logger log = LoggerFactory.getLogger(AiConfig.class);

    /**
     * The provider is chosen by configuration (AI_PROVIDER). Without a provider or API key the reports
     * are written by the rule-based writer, so the feature keeps working offline.
     */
    @Bean
    public AiClient aiClient(AiProperties props) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(props.timeout());
        factory.setReadTimeout(props.timeout());
        RestClient.Builder builder = RestClient.builder().requestFactory(factory);

        AiClient client = switch (props.provider() == null ? "mock" : props.provider().toLowerCase()) {
            case "gemini" -> new GeminiClient(props.gemini(), builder);
            case "openai" -> new OpenAiClient(props.openai(), builder);
            default -> null;
        };
        if (client != null && !client.isConfigured()) {
            log.warn("AI provider '{}' selected but no API key configured: using the rule-based writer", client.provider());
            return new RuleBasedOnlyClient();
        }
        if (client == null) {
            log.info("AI reports provider: mock (rule-based writer)");
            return new RuleBasedOnlyClient();
        }
        log.info("AI reports provider: {} / {}", client.provider(), client.model());
        return client;
    }
}
