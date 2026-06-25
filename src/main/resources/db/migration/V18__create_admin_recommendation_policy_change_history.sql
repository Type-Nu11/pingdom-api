CREATE TABLE IF NOT EXISTS admin_recommendation_policy_change_history (
    id BIGSERIAL PRIMARY KEY,
    recommendation_version VARCHAR(100) NOT NULL,
    change_type VARCHAR(50) NOT NULL,
    actor_user_id BIGINT,
    reason VARCHAR(500),
    before_state TEXT NOT NULL,
    after_state TEXT NOT NULL,
    changed_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_admin_recommendation_policy_change_history_created
    ON admin_recommendation_policy_change_history (changed_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_admin_recommendation_policy_change_history_version
    ON admin_recommendation_policy_change_history (recommendation_version, changed_at DESC);

CREATE INDEX IF NOT EXISTS idx_admin_recommendation_policy_change_history_actor
    ON admin_recommendation_policy_change_history (actor_user_id, changed_at DESC);
