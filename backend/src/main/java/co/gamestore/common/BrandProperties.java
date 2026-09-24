package co.gamestore.common;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Everything about the store's identity that is not decided yet.
 *
 * <p>The working name and the domain are placeholders, and the real ones arrive later. Keeping them
 * in configuration means changing them is an environment variable, not a search across the codebase
 * with the risk of missing the one place that mattered.
 *
 * @param name what customers and authenticator apps see
 * @param domain the domain the store will live on, without a scheme
 * @param supportEmail where customers write about an order or a repair
 * @param dataProtectionEmail the channel Ley 1581 requires for exercising data rights
 * @param problemBaseUri the prefix of the RFC 9457 error type URIs. Changing it after clients rely on
 *     the old values breaks them, so it is configurable for a rename and then left alone.
 */
@ConfigurationProperties(prefix = "app.brand")
public record BrandProperties(
        String name,
        String domain,
        String supportEmail,
        String dataProtectionEmail,
        String problemBaseUri) {
}
