package co.gamestore.security;

/**
 * What a customer can agree to, one purpose at a time.
 *
 * <p>Decreto 1377 requires the purposes to be specific: a single box covering everything is not
 * informed consent. Serving an order is not marketing, and marketing by email is not marketing by
 * WhatsApp, which Ley 2300 treats differently again.
 */
public enum ConsentPurpose {

    /** Handling the account and the orders. Without it there is no service to give. */
    DATA_PROCESSING,
    MARKETING_EMAIL,
    MARKETING_WHATSAPP,
    ANALYTICS
}
