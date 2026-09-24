# Database migrations

The database schema is owned by Flyway. Hibernate never creates or alters it: the application runs
with `spring.jpa.hibernate.ddl-auto=validate`, so it refuses to start when the code and the schema
disagree. That check is the point of this setup.

## Where the files live

```
backend/src/main/resources/db/migration/
  V1__baseline.sql
```

Flyway applies every `V<n>__<description>.sql` in order and records it in `flyway_schema_history`.

## Rules

1. **One file per change.** Never edit a migration that has already run anywhere, not even to fix a
   typo. Write the next one instead. Flyway stores a checksum and will refuse to start if it changes.
2. **Version numbers are sequential integers**, and the description is lowercase words separated by
   underscores: `V7__add_repair_tickets.sql`.
3. **Name every constraint and index.** `pk_`, `ux_`, `fk_`, `ck_`, `ix_` followed by the table and
   the columns. Unnamed constraints get random names that differ between environments.
4. **Snake case** for tables and columns, plural table names.
5. **`TIMESTAMP WITH TIME ZONE` for every instant**, stored in UTC. Local time is a display concern.
6. **Money is `NUMERIC(19, 2)`** with the currency in its own column. Never floating point.
7. **No data in a schema migration** beyond reference data that the code depends on. Demo and seed
   data belong to the seeder, which is disabled outside development.
8. **Repeatable migrations** (`R__`) only for views and functions that can be dropped and recreated.

## Zero-downtime changes (expand and contract)

While two versions of the application run at the same time, a migration must work for both. Splitting
a change into steps is the difference between a deploy and an outage.

| Step | What runs | Safe because |
|---|---|---|
| Expand | Add the new nullable column or table, backfill in batches | Old code ignores it |
| Migrate | Deploy code that writes both old and new, reads new | Both shapes are valid |
| Contract | Drop the old column in a later release | Nothing reads it any more |

Never do in one migration: rename a column, change a type in place, or add a `NOT NULL` column
without a default. Each of those breaks the replica that has not been updated yet.

Adding an index on a large table uses `CREATE INDEX CONCURRENTLY`, which cannot run inside a
transaction, so it goes in its own migration file.

## Checking a migration before it ships

```bash
docker compose up -d postgres
docker compose run --rm api java -jar app.jar --spring.flyway.validate-on-migrate=true
```

The test suite does this on every run: Testcontainers starts an empty PostgreSQL, Flyway applies
every migration from scratch and Hibernate validates the result against the entities. A migration
that does not match the code fails the build rather than the deploy.
