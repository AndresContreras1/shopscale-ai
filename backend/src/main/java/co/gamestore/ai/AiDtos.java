package co.gamestore.ai;

import jakarta.validation.constraints.Pattern;
import java.io.Serializable;
import java.time.Instant;

public final class AiDtos {

    private AiDtos() {
    }

    public enum ReportType { INVENTORY, SALES, PRODUCT_DESCRIPTION }

    public record ReportRequest(@Pattern(regexp = "^(en|es)$", message = "must be en or es") String language) {

        public String languageOrDefault() {
            return language == null ? "en" : language;
        }
    }

    /**
     * @param fallback true when the AI provider failed or is not configured and the rule-based writer answered
     * @param facts    the computed numbers the report is based on, so the UI can show them next to the text
     */
    public record AiReport(ReportType type, String provider, String model, boolean fallback, long latencyMs,
                           Instant generatedAt, String markdown, Object facts) implements Serializable {
    }

    public record AiStatus(String provider, String model, boolean liveProvider) {
    }
}
