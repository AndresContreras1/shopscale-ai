package co.gamestore.ai;

/**
 * A text-generation provider. Implementations are interchangeable, so the rest of the system does not
 * depend on any vendor.
 */
public interface AiClient {

    /** Provider name shown in reports, e.g. "gemini". */
    String provider();

    String model();

    /** Whether the provider has what it needs (e.g. an API key) to be called. */
    boolean isConfigured();

    String complete(String systemPrompt, String userPrompt);
}
