-- Transactional outbox for domain events (Spring Modulith event publication registry).
-- A listener that fails leaves its row incomplete, so the event can be replayed instead of lost.
-- DDL taken from spring-modulith-events-jdbc (schema-postgresql.sql) so it matches what the
-- framework expects; it is applied here rather than by schema initialization at startup.

CREATE TABLE event_publication (
    id               UUID NOT NULL,
    listener_id      TEXT NOT NULL,
    event_type       TEXT NOT NULL,
    serialized_event TEXT NOT NULL,
    publication_date TIMESTAMP WITH TIME ZONE NOT NULL,
    completion_date  TIMESTAMP WITH TIME ZONE,
    CONSTRAINT pk_event_publication PRIMARY KEY (id)
);

CREATE INDEX ix_event_publication_serialized_event_hash
    ON event_publication USING HASH (serialized_event);
CREATE INDEX ix_event_publication_completion_date
    ON event_publication (completion_date);
