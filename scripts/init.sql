-- Zero Trust Gateway - Database Initialization

CREATE TABLE IF NOT EXISTS audit_log (
    id              BIGSERIAL PRIMARY KEY,
    request_id      UUID NOT NULL,
    timestamp       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    client_ip       VARCHAR(45) NOT NULL,
    user_id         VARCHAR(255),
    method          VARCHAR(10) NOT NULL,
    path            VARCHAR(2048) NOT NULL,
    status_code     INT,
    filter_name     VARCHAR(64),
    decision        VARCHAR(16),
    threat_score    DOUBLE PRECISION,
    latency_ms      BIGINT,
    details         JSONB
);

CREATE INDEX idx_audit_log_timestamp ON audit_log(timestamp);
CREATE INDEX idx_audit_log_user_id ON audit_log(user_id);
CREATE INDEX idx_audit_log_client_ip ON audit_log(client_ip);
CREATE INDEX idx_audit_log_decision ON audit_log(decision);

CREATE TABLE IF NOT EXISTS token_blacklist (
    jti             VARCHAR(255) PRIMARY KEY,
    user_id         VARCHAR(255) NOT NULL,
    blacklisted_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at      TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_token_blacklist_expires ON token_blacklist(expires_at);

CREATE TABLE IF NOT EXISTS user_behavior_profile (
    user_id             VARCHAR(255) PRIMARY KEY,
    typical_user_agents TEXT[],
    typical_hours       INT[],
    typical_routes      TEXT[],
    avg_request_rate    DOUBLE PRECISION DEFAULT 0,
    last_known_ip       VARCHAR(45),
    last_known_country  VARCHAR(3),
    last_seen_at        TIMESTAMPTZ,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS geo_login_history (
    id              BIGSERIAL PRIMARY KEY,
    user_id         VARCHAR(255) NOT NULL,
    ip_address      VARCHAR(45) NOT NULL,
    country_code    VARCHAR(3),
    city            VARCHAR(255),
    latitude        DOUBLE PRECISION,
    longitude       DOUBLE PRECISION,
    logged_in_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_geo_login_user ON geo_login_history(user_id, logged_in_at DESC);

CREATE TABLE IF NOT EXISTS blocked_countries (
    country_code    VARCHAR(3) PRIMARY KEY,
    reason          VARCHAR(512),
    blocked_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Insert some default blocked countries (example)
INSERT INTO blocked_countries (country_code, reason) VALUES
    ('KP', 'OFAC sanctions'),
    ('IR', 'OFAC sanctions'),
    ('SY', 'OFAC sanctions')
ON CONFLICT DO NOTHING;

CREATE TABLE IF NOT EXISTS circuit_breaker_state (
    service_name    VARCHAR(255) PRIMARY KEY,
    state           VARCHAR(16) NOT NULL DEFAULT 'CLOSED',
    failure_count   INT NOT NULL DEFAULT 0,
    last_failure_at TIMESTAMPTZ,
    opened_at       TIMESTAMPTZ,
    half_open_at    TIMESTAMPTZ,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS model_versions (
    id              BIGSERIAL PRIMARY KEY,
    model_name      VARCHAR(255) NOT NULL,
    version         VARCHAR(64) NOT NULL,
    model_path      VARCHAR(1024) NOT NULL,
    accuracy        DOUBLE PRECISION,
    is_active       BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    activated_at    TIMESTAMPTZ
);

CREATE INDEX idx_model_versions_active ON model_versions(model_name, is_active);
