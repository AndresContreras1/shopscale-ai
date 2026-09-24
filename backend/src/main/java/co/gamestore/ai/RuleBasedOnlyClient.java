package co.gamestore.ai;

/**
 * Placeholder when no AI provider is configured: reports are produced by {@link RuleBasedReportWriter}.
 */
class RuleBasedOnlyClient implements AiClient {

    @Override
    public String provider() {
        return "mock";
    }

    @Override
    public String model() {
        return "rule-based";
    }

    @Override
    public boolean isConfigured() {
        return false;
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        throw new UnsupportedOperationException("No AI provider configured");
    }
}
