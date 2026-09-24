package co.gamestore.common;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * An amount and its currency, kept together so they cannot drift apart.
 *
 * <p>The amount is stored in minor units as a whole number: centavos for COP, cents for USD. Money is
 * never a {@code double}: a price of 0.1 that cannot be represented in binary turns into a cent of
 * difference per line and an invoice that does not add up.
 *
 * <p>ISO 4217 gives COP two decimals and the payment gateways count centavos, so that is what is
 * stored. Colombia does not use them in practice, so {@link #format(Locale)} hides them.
 *
 * <p>Rounding is always {@link RoundingMode#HALF_EVEN}, the rule accountants use, because always
 * rounding halves upwards biases every total in the same direction.
 *
 * <p>Prices are tax inclusive: what the customer sees is what the customer pays.
 */
public record Money(long amountMinor, Currency currency) implements Comparable<Money> {

    public static final Currency COP = Currency.getInstance("COP");

    /** Centavos exist in the ledger and in every gateway payload, but never on a price tag. */
    private static final int COP_DISPLAY_DIGITS = 0;

    public Money {
        Objects.requireNonNull(currency, "currency is required");
    }

    /** Builds an amount from minor units, the shape payment gateways use. */
    public static Money ofMinor(long amountMinor, Currency currency) {
        return new Money(amountMinor, currency);
    }

    /** Builds an amount from major units, for example 1299900 pesos or 19.99 dollars. */
    public static Money of(BigDecimal amount, Currency currency) {
        Objects.requireNonNull(amount, "amount is required");
        Objects.requireNonNull(currency, "currency is required");
        BigDecimal minor = amount.movePointRight(currency.getDefaultFractionDigits())
                .setScale(0, RoundingMode.HALF_EVEN);
        return new Money(minor.longValueExact(), currency);
    }

    public static Money of(String amount, Currency currency) {
        return of(new BigDecimal(amount), currency);
    }

    /** Colombian pesos, the store's currency. The argument is pesos, not centavos. */
    public static Money cop(long pesos) {
        return of(BigDecimal.valueOf(pesos), COP);
    }

    public static Money cop(BigDecimal pesos) {
        return of(pesos, COP);
    }

    public static Money zero(Currency currency) {
        return new Money(0L, currency);
    }

    /** The amount in major units, which is what goes into a NUMERIC(19, 2) column. */
    public BigDecimal amount() {
        return BigDecimal.valueOf(amountMinor, currency.getDefaultFractionDigits());
    }

    public Money plus(Money other) {
        return new Money(Math.addExact(amountMinor, sameCurrency(other).amountMinor), currency);
    }

    public Money minus(Money other) {
        return new Money(Math.subtractExact(amountMinor, sameCurrency(other).amountMinor), currency);
    }

    public Money times(long factor) {
        return new Money(Math.multiplyExact(amountMinor, factor), currency);
    }

    /** Applies a rate such as a 19% tax or a 15% discount. */
    public Money percentage(BigDecimal rate) {
        BigDecimal result = BigDecimal.valueOf(amountMinor)
                .multiply(rate)
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_EVEN);
        return new Money(result.longValueExact(), currency);
    }

    public Money negated() {
        return new Money(Math.negateExact(amountMinor), currency);
    }

    /**
     * Splits the amount into equal parts without losing a single minor unit: the remainder is spread
     * one unit at a time over the first parts. Three ways of 100 pesos is 34, 33, 33, never 33.33.
     */
    public List<Money> allocate(int parts) {
        if (parts < 1) {
            throw new IllegalArgumentException("parts must be at least 1");
        }
        long base = amountMinor / parts;
        long remainder = amountMinor % parts;
        List<Money> result = new ArrayList<>(parts);
        for (int i = 0; i < parts; i++) {
            result.add(new Money(base + (i < Math.abs(remainder) ? Long.signum(remainder) : 0), currency));
        }
        return List.copyOf(result);
    }

    public boolean isZero() {
        return amountMinor == 0;
    }

    public boolean isPositive() {
        return amountMinor > 0;
    }

    public boolean isNegative() {
        return amountMinor < 0;
    }

    /** Formats for display, so COP shows as $ 1.299.900 and never as $ 1.299.900,00. */
    public String format(Locale locale) {
        int digits = COP.equals(currency) ? COP_DISPLAY_DIGITS : currency.getDefaultFractionDigits();
        NumberFormat format = NumberFormat.getCurrencyInstance(locale);
        format.setCurrency(currency);
        format.setMinimumFractionDigits(digits);
        format.setMaximumFractionDigits(digits);
        return format.format(amount());
    }

    @Override
    public int compareTo(Money other) {
        return Long.compare(amountMinor, sameCurrency(other).amountMinor);
    }

    @Override
    public String toString() {
        return amount().toPlainString() + " " + currency.getCurrencyCode();
    }

    private Money sameCurrency(Money other) {
        Objects.requireNonNull(other, "the other amount is required");
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException(
                    "Cannot mix " + currency.getCurrencyCode() + " and " + other.currency.getCurrencyCode());
        }
        return other;
    }
}
