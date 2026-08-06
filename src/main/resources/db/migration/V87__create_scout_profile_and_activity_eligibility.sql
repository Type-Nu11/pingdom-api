CREATE TABLE scout_profile (
    user_id BIGINT PRIMARY KEY,
    display_name VARCHAR(100) NOT NULL,
    introduction VARCHAR(1000),
    status VARCHAR(20) NOT NULL,
    reviewed_by_admin_user_id BIGINT,
    reviewed_at TIMESTAMP(6),
    status_reason VARCHAR(500),
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_scout_profile_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_scout_profile_reviewer
        FOREIGN KEY (reviewed_by_admin_user_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT ck_scout_profile_user
        CHECK (user_id > 0),
    CONSTRAINT ck_scout_profile_display_name
        CHECK (char_length(btrim(display_name)) > 0),
    CONSTRAINT ck_scout_profile_status
        CHECK (status IN ('PENDING', 'ACTIVE', 'SUSPENDED', 'REVOKED')),
    CONSTRAINT ck_scout_profile_state
        CHECK (
            (status = 'PENDING' AND reviewed_by_admin_user_id IS NULL AND reviewed_at IS NULL AND status_reason IS NULL)
            OR (status = 'ACTIVE' AND status_reason IS NULL)
            OR (status IN ('SUSPENDED', 'REVOKED') AND char_length(btrim(status_reason)) > 0)
        ),
    CONSTRAINT ck_scout_profile_version
        CHECK (version >= 0)
);

CREATE TABLE scout_activity_eligibility (
    scout_user_id BIGINT PRIMARY KEY,
    status VARCHAR(20) NOT NULL,
    eligible_from TIMESTAMP(6),
    eligible_until TIMESTAMP(6),
    reviewed_by_admin_user_id BIGINT,
    reviewed_at TIMESTAMP(6),
    status_reason VARCHAR(500),
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_scout_activity_eligibility_profile
        FOREIGN KEY (scout_user_id) REFERENCES scout_profile (user_id) ON DELETE CASCADE,
    CONSTRAINT fk_scout_activity_eligibility_reviewer
        FOREIGN KEY (reviewed_by_admin_user_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT ck_scout_activity_eligibility_user
        CHECK (scout_user_id > 0),
    CONSTRAINT ck_scout_activity_eligibility_status
        CHECK (status IN ('PENDING', 'ELIGIBLE', 'SUSPENDED', 'EXPIRED', 'REVOKED')),
    CONSTRAINT ck_scout_activity_eligibility_period
        CHECK (eligible_until IS NULL OR (eligible_from IS NOT NULL AND eligible_until > eligible_from)),
    CONSTRAINT ck_scout_activity_eligibility_lifecycle
        CHECK (
            (status = 'PENDING' AND eligible_from IS NULL AND eligible_until IS NULL)
            OR (status IN ('ELIGIBLE', 'SUSPENDED', 'REVOKED') AND eligible_from IS NOT NULL)
            OR (status = 'EXPIRED' AND eligible_from IS NOT NULL AND eligible_until IS NOT NULL)
        ),
    CONSTRAINT ck_scout_activity_eligibility_version
        CHECK (version >= 0)
);

CREATE INDEX idx_scout_profile_status_updated
    ON scout_profile (status, updated_at DESC, user_id DESC);

CREATE INDEX idx_scout_activity_eligibility_status_period
    ON scout_activity_eligibility (status, eligible_from, eligible_until, scout_user_id);

-- Existing field reporters already passed the legacy Scout activity boundary.
-- Keep their ability to continue reporting when the explicit qualification model is introduced.
INSERT INTO scout_profile (
    user_id, display_name, introduction, status,
    reviewed_by_admin_user_id, reviewed_at, status_reason,
    created_at, updated_at, version
)
SELECT DISTINCT ON (report.scout_user_id)
    report.scout_user_id,
    LEFT(COALESCE(NULLIF(BTRIM(user_account.username), ''), '기존 Scout'), 100),
    NULL,
    'ACTIVE',
    NULL,
    NULL,
    NULL,
    COALESCE(user_account.created_at, CURRENT_TIMESTAMP),
    CURRENT_TIMESTAMP,
    0
FROM scout_field_report report
JOIN users user_account ON user_account.id = report.scout_user_id
WHERE report.scout_user_id IS NOT NULL
ORDER BY report.scout_user_id, report.created_at ASC, report.id ASC
ON CONFLICT (user_id) DO NOTHING;

INSERT INTO scout_activity_eligibility (
    scout_user_id, status, eligible_from, eligible_until,
    reviewed_by_admin_user_id, reviewed_at, status_reason,
    created_at, updated_at, version
)
SELECT profile.user_id,
       'ELIGIBLE',
       MIN(report.created_at),
       NULL,
       NULL,
       NULL,
       NULL,
       MIN(report.created_at),
       CURRENT_TIMESTAMP,
       0
FROM scout_profile profile
JOIN scout_field_report report ON report.scout_user_id = profile.user_id
GROUP BY profile.user_id
ON CONFLICT (scout_user_id) DO NOTHING;
