CREATE TABLE mini_app_developer_account (
    id BIGINT NOT NULL AUTO_INCREMENT,
    username VARCHAR(40) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    role VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    purpose VARCHAR(500) NOT NULL,
    plan_description VARCHAR(1000) NOT NULL,
    developer_name VARCHAR(80) NOT NULL,
    contact_email VARCHAR(254) NOT NULL,
    organization_name VARCHAR(120) NULL,
    review_note VARCHAR(500) NULL,
    reviewed_by BIGINT NULL,
    reviewed_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_mini_app_developer_username UNIQUE (username),
    CONSTRAINT uk_mini_app_developer_email UNIQUE (contact_email),
    CONSTRAINT fk_mini_app_developer_reviewer FOREIGN KEY (reviewed_by)
        REFERENCES mini_app_developer_account (id)
);

CREATE TABLE mini_app_platform_session (
    id BIGINT NOT NULL AUTO_INCREMENT,
    account_id BIGINT NOT NULL,
    token_hash CHAR(64) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_mini_app_session_token UNIQUE (token_hash),
    CONSTRAINT fk_mini_app_session_account FOREIGN KEY (account_id)
        REFERENCES mini_app_developer_account (id) ON DELETE CASCADE
);

CREATE INDEX idx_mini_app_session_expiry ON mini_app_platform_session (expires_at);

CREATE TABLE mini_app (
    id BIGINT NOT NULL AUTO_INCREMENT,
    app_id VARCHAR(120) NOT NULL,
    name VARCHAR(40) NOT NULL,
    description VARCHAR(120) NOT NULL,
    developer_account_id BIGINT NOT NULL,
    developer_name VARCHAR(80) NOT NULL,
    icon_object_key VARCHAR(500) NULL,
    cover_object_key VARCHAR(500) NULL,
    status VARCHAR(20) NOT NULL,
    current_version_id BIGINT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_mini_app_app_id UNIQUE (app_id),
    CONSTRAINT fk_mini_app_developer FOREIGN KEY (developer_account_id)
        REFERENCES mini_app_developer_account (id)
);

CREATE INDEX idx_mini_app_developer ON mini_app (developer_account_id);

CREATE TABLE mini_app_version (
    id BIGINT NOT NULL AUTO_INCREMENT,
    mini_app_id BIGINT NOT NULL,
    schema_version INT NOT NULL,
    manifest_app_id VARCHAR(120) NOT NULL,
    manifest_name VARCHAR(40) NOT NULL,
    version VARCHAR(40) NOT NULL,
    entry_path VARCHAR(500) NOT NULL,
    permissions_json TEXT NOT NULL,
    manifest_description VARCHAR(120) NOT NULL,
    object_key VARCHAR(500) NOT NULL,
    archive_sha256 CHAR(64) NOT NULL,
    archive_size BIGINT NOT NULL,
    release_notes VARCHAR(1000) NOT NULL,
    status VARCHAR(30) NOT NULL,
    review_note VARCHAR(500) NULL,
    reviewed_by BIGINT NULL,
    reviewed_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_mini_app_version UNIQUE (mini_app_id, version),
    CONSTRAINT fk_mini_app_version_app FOREIGN KEY (mini_app_id)
        REFERENCES mini_app (id) ON DELETE CASCADE,
    CONSTRAINT fk_mini_app_version_reviewer FOREIGN KEY (reviewed_by)
        REFERENCES mini_app_developer_account (id)
);

CREATE INDEX idx_mini_app_version_status ON mini_app_version (status, created_at);

ALTER TABLE mini_app ADD CONSTRAINT fk_mini_app_current_version
    FOREIGN KEY (current_version_id) REFERENCES mini_app_version (id);

CREATE TABLE mini_app_rating (
    id BIGINT NOT NULL AUTO_INCREMENT,
    mini_app_id BIGINT NOT NULL,
    uchat_user_id BIGINT NOT NULL,
    score INT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_mini_app_rating_user UNIQUE (mini_app_id, uchat_user_id),
    CONSTRAINT fk_mini_app_rating_app FOREIGN KEY (mini_app_id)
        REFERENCES mini_app (id) ON DELETE CASCADE,
    CONSTRAINT chk_mini_app_rating_score CHECK (score >= 1 AND score <= 5)
);

CREATE TABLE mini_app_comment (
    id BIGINT NOT NULL AUTO_INCREMENT,
    mini_app_id BIGINT NOT NULL,
    uchat_user_id BIGINT NOT NULL,
    user_display_name VARCHAR(120) NOT NULL,
    content VARCHAR(500) NOT NULL,
    is_featured BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(20) NOT NULL DEFAULT 'VISIBLE',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_mini_app_comment_user UNIQUE (mini_app_id, uchat_user_id),
    CONSTRAINT fk_mini_app_comment_app FOREIGN KEY (mini_app_id)
        REFERENCES mini_app (id) ON DELETE CASCADE
);

CREATE INDEX idx_mini_app_comment_app ON mini_app_comment (mini_app_id, status, created_at);
