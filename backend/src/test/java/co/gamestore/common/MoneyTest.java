package co.gamestore.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class MoneyTest {

    private static final Currency USD = Currency.getInstance("USD");

    @Test
    void copIsStoredInCentavos() {
        Money price = Money.cop(1_299_900);

        assertThat(price.amountMinor()).isEqualTo(129_990_000L);
        assertThat(price.amount()).isEqualByComparingTo("1299900.00");
    }

    @Test
    void usdIsStoredInCents() {
        Money price = Money.of("19.99", USD);

        assertThat(price.amountMinor()).isEqualTo(1999);
        assertThat(price.amount()).isEqualByComparingTo("19.99");
    }

    @Test
    void roundsHalfToEven() {
        assertThat(Money.of("0.125", USD).amountMinor()).isEqualTo(12);
        assertThat(Money.of("0.135", USD).amountMinor()).isEqualTo(14);
    }

    @Test
    void addsAndSubtractsWithinOneCurrency() {
        Money total = Money.cop(100_000).plus(Money.cop(50_000)).minus(Money.cop(20_000));

        assertThat(total).isEqualTo(Money.cop(130_000));
    }

    @Test
    void refusesToMixCurrencies() {
        assertThatThrownBy(() -> Money.cop(1000).plus(Money.of("1.00", USD)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("COP")
                .hasMessageContaining("USD");
    }

    @Test
    void appliesAPercentage() {
        assertThat(Money.cop(1_000_000).percentage(new BigDecimal("19"))).isEqualTo(Money.cop(190_000));
    }

    @Test
    void exposesMinorUnitsForPaymentGateways() {
        assertThat(Money.ofMinor(129_990_000L, Money.COP)).isEqualTo(Money.cop(1_299_900));
    }

    @Test
    void allocateNeverLosesAUnit() {
        var parts = Money.ofMinor(100, Money.COP).allocate(3);

        assertThat(parts).containsExactly(Money.ofMinor(34, Money.COP), Money.ofMinor(33, Money.COP),
                Money.ofMinor(33, Money.COP));
        assertThat(parts.stream().reduce(Money.zero(Money.COP), Money::plus))
                .isEqualTo(Money.ofMinor(100, Money.COP));
    }

    @Test
    void formatsColombianPesosWithoutDecimals() {
        String formatted = Money.cop(1_299_900).format(Locale.forLanguageTag("es-CO"))
                .replace('\u00A0', ' ')
                .replace('\u202F', ' ');

        assertThat(formatted).contains("1.299.900").doesNotContain(",00");
    }

    @Test
    void comparesAmounts() {
        assertThat(Money.cop(10)).isLessThan(Money.cop(20));
        assertThat(Money.cop(-5).isNegative()).isTrue();
        assertThat(Money.zero(Money.COP).isZero()).isTrue();
    }
}
