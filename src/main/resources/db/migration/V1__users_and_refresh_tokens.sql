CREATE TABLE users (
    id            bigserial    PRIMARY KEY,
    username      varchar(50)  NOT NULL UNIQUE,
    password_hash varchar(100) NOT NULL,
    enabled       boolean      NOT NULL DEFAULT true,
    created_at    timestamptz  NOT NULL DEFAULT now()
);

CREATE TABLE refresh_tokens (
    id          bigserial   PRIMARY KEY,
    user_id     bigint      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    family_id   uuid        NOT NULL,
    token_hash  char(64)    NOT NULL UNIQUE,
    created_at  timestamptz NOT NULL DEFAULT now(),
    expires_at  timestamptz NOT NULL,
    used_at     timestamptz,
    revoked_at  timestamptz
);

CREATE INDEX idx_refresh_tokens_family_id ON refresh_tokens (family_id);
