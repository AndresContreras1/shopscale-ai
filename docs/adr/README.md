# Architecture decision records

One file per decision that is expensive to reverse. Each records what was decided, why, and what it
costs, so the reasoning survives the people who were in the room.

| # | Decision | Status |
|---|---|---|
| [001](ADR-001-modular-monolith.md) | Modular monolith with Spring Modulith | Accepted |
| [002](ADR-002-money.md) | Money as a value object in minor units | Accepted |
| [004](ADR-004-identifiers.md) | Internal identity keys, public UUIDv7 | Accepted |
| [005](ADR-005-errors.md) | RFC 9457 problem details with a stable error catalog | Accepted |

The remaining records in the [master plan](../master-plan.md) arrive with the phase that implements
them: time and time zones (003), idempotency (006), state machines (007), sessions over JWT (008),
facts before AI (009) and provider ports (010).
