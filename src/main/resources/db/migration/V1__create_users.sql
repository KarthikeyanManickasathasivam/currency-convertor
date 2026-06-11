CREATE TABLE users (
    user_id     UUID            NOT NULL DEFAULT gen_random_uuid(),
    username    VARCHAR(50)     NOT NULL,
    email       VARCHAR(255)    NOT NULL,
    password_hash VARCHAR(255)  NOT NULL,
    role        VARCHAR(20)     NOT NULL DEFAULT 'USER',
    is_active   BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP       NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_users PRIMARY KEY (user_id),
    CONSTRAINT uq_users_username UNIQUE (username),
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT chk_users_role CHECK (role IN ('USER', 'ADMIN'))
);

CREATE INDEX idx_users_email ON users (email);
CREATE INDEX idx_users_username ON users (username);
CREATE INDEX idx_users_is_active ON users (is_active);
