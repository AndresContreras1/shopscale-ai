# ADR-002: Money as a value object in minor units

**Status:** Accepted · **Date:** 2026-09-24

## Context

A store gets money wrong in three classic ways: floating point arithmetic, an amount without its
currency, and rounding that always favours the same direction. All three produce invoices that do
not add up, which in Colombia is a DIAN problem and not only an accounting one.

Colombian pesos carry two decimals in ISO 4217 and every payment gateway counts centavos, but no
price tag in Colombia shows them. Storage and display are not the same question.

## Decision

`Money(long amountMinor, Currency currency)` in the shared kernel. The amount is a whole number of
minor units: centavos for COP, cents for USD. That is the unit the gateways speak, so no conversion
happens on the way to a payment.

- Arithmetic lives on the type. Adding two different currencies throws.
- Rounding is always `HALF_EVEN`, the banker's rule, because `HALF_UP` biases every total upwards.
- `allocate(parts)` splits without losing a minor unit: 100 centavos in three is 34, 33, 33.
- Stored as `NUMERIC(19, 2)` for the amount plus `CHAR(3)` for the ISO 4217 code. Two columns, never
  a single number whose currency lives in someone's head.
- Prices are **tax inclusive**, which is what Ley 1480 requires the customer to see. The tax
  breakdown is derived for the invoice, not added at the end.
- Each payment provider has its own idea of minor units, so the conversion happens in that
  provider's adapter and nowhere else.

## Consequences

- No `double` and no bare `BigDecimal` for money anywhere in new code.
- Existing columns (`products.price`, `orders.total`, `order_items.unit_price`) still hold a plain
  `BigDecimal` in COP. They migrate when the catalog is rewritten in phase 2, which is where the tax
  class and the multi-currency question arrive together. Until then `Money` is the type used by
  everything new.
- Display is a separate concern from storage: `format(Locale)` renders COP without decimals, which is
  what a Colombian customer expects to read.
