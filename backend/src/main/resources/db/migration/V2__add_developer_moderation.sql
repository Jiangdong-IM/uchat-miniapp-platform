ALTER TABLE mini_app_developer_account
    ADD COLUMN ban_reason VARCHAR(500) NULL;

ALTER TABLE mini_app_developer_account
    ADD COLUMN banned_by BIGINT NULL;

ALTER TABLE mini_app_developer_account
    ADD COLUMN banned_at TIMESTAMP(6) NULL;

ALTER TABLE mini_app_developer_account
    ADD CONSTRAINT fk_mini_app_developer_banner FOREIGN KEY (banned_by)
        REFERENCES mini_app_developer_account (id);

CREATE TABLE mini_app_developer_appeal (
    id BIGINT NOT NULL AUTO_INCREMENT,
    developer_account_id BIGINT NOT NULL,
    content VARCHAR(1000) NOT NULL,
    status VARCHAR(20) NOT NULL,
    review_note VARCHAR(500) NULL,
    reviewed_by BIGINT NULL,
    reviewed_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_mini_app_appeal_developer FOREIGN KEY (developer_account_id)
        REFERENCES mini_app_developer_account (id) ON DELETE CASCADE,
    CONSTRAINT fk_mini_app_appeal_reviewer FOREIGN KEY (reviewed_by)
        REFERENCES mini_app_developer_account (id)
);

CREATE INDEX idx_mini_app_appeal_developer
    ON mini_app_developer_appeal (developer_account_id, created_at);

CREATE INDEX idx_mini_app_appeal_status
    ON mini_app_developer_appeal (status, created_at);
