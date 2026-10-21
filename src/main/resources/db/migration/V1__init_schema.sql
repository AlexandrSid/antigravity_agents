-- V1__init_schema.sql
-- Anti-Gravity Agents: link-shortener Greenfield DDL Schema

-- Short links table
CREATE TABLE short_links (
    id           BIGINT        NOT NULL,
    short_key    VARCHAR(8)    NOT NULL,
    original_url VARCHAR(2048) NOT NULL,
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    expires_at   TIMESTAMPTZ   NOT NULL,
    CONSTRAINT pk_short_links PRIMARY KEY (id),
    CONSTRAINT uq_short_links_short_key UNIQUE (short_key)
);

-- Index for fast lookup by short_key
CREATE INDEX idx_short_links_short_key ON short_links (short_key);

-- Index for cleanup job queries (expires_at < NOW())
CREATE INDEX idx_short_links_expires_at ON short_links (expires_at);

-- Sequence for ID generation with allocation size 1000
-- Matches Panache @SequenceGenerator(allocationSize = 1000)
CREATE SEQUENCE short_links_seq
    START WITH 1
    INCREMENT BY 1000
    NO CYCLE;
