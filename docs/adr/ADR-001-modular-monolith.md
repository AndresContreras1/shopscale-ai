# ADR-001: Modular monolith with Spring Modulith

**Status:** Accepted · **Date:** 2026-09-24

## Context

The store has clearly separate areas: catalog, inventory, orders, payments, shipping, repairs. They
are separate enough to be services, but the project is one person operating one product. Splitting
into microservices now buys independent deployment and pays with network calls, distributed
transactions, duplicated data and an operations bill that a single VPS cannot carry.

The opposite failure is just as real: one package tangled enough that no boundary can ever be found
again.

## Decision

One deployable application, internally divided into modules. Each package directly under
`co.gamestore` is a module: it publishes the types in its own top-level package and hides everything
else in nested packages. `common` is the shared kernel every module may use.

Spring Modulith enforces this in a test, not in a document. `ApplicationModules.verify()` fails the
build when a module reaches into another module's internals, and when two modules depend on each
other. Modules that must react to each other communicate through domain events, which are written to
`event_publication` in the same transaction as the change that produced them.

## Consequences

- A boundary violation is a red build, not a code review comment somebody may miss.
- A cycle has to be resolved by design. The catalog needed stock and the inventory needed products;
  the catalog now depends on `StockPort` in the shared kernel, which the inventory implements.
- Extracting a module into its own service later is mechanical rather than archaeological.
- The cost is ceremony: crossing a boundary takes an interface or an event, not a direct call.
- Deployment stays one image, one database, one set of logs, which is the point at this size.
