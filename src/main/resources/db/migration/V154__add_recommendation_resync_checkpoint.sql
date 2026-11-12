CREATE TABLE recommendation_resync_checkpoint (
    id SMALLINT PRIMARY KEY CHECK (id = 1),
    owner_token UUID,
    lease_until TIMESTAMPTZ,
    phase VARCHAR(32) NOT NULL DEFAULT 'DONE',
    cursor_left BIGINT NOT NULL DEFAULT 0,
    cursor_right BIGINT NOT NULL DEFAULT 0,
    upper_place_id BIGINT NOT NULL DEFAULT 0,
    upper_snapshot_id BIGINT NOT NULL DEFAULT 0,
    upper_version_id BIGINT NOT NULL DEFAULT 0,
    upper_similarity_id BIGINT NOT NULL DEFAULT 0,
    total_bookmark_users BIGINT NOT NULL DEFAULT 0,
    place_count BIGINT NOT NULL DEFAULT 0,
    synchronized_count BIGINT NOT NULL DEFAULT 0,
    deleted_count BIGINT NOT NULL DEFAULT 0,
    synchronized_similarity_count BIGINT NOT NULL DEFAULT 0,
    deleted_similarity_count BIGINT NOT NULL DEFAULT 0,
    synchronized_version_count BIGINT NOT NULL DEFAULT 0,
    deleted_version_count BIGINT NOT NULL DEFAULT 0
);
INSERT INTO recommendation_resync_checkpoint (id) VALUES (1);
