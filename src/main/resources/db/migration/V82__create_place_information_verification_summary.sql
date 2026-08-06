CREATE TABLE place_information_verification_summary (
    map_place_id BIGINT PRIMARY KEY,
    verified_evidence_count INTEGER NOT NULL DEFAULT 0,
    last_verified_evidence_id BIGINT,
    last_verified_source_type VARCHAR(30),
    last_verified_at TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_place_information_summary_place
        FOREIGN KEY (map_place_id) REFERENCES map_place (map_place_id) ON DELETE CASCADE,
    CONSTRAINT fk_place_information_summary_evidence
        FOREIGN KEY (last_verified_evidence_id)
        REFERENCES place_information_evidence (place_information_evidence_id) ON DELETE SET NULL,
    CONSTRAINT ck_place_information_summary_count CHECK (verified_evidence_count >= 0),
    CONSTRAINT ck_place_information_summary_source CHECK (
        last_verified_source_type IS NULL OR last_verified_source_type IN (
            'LEGACY', 'KAKAO', 'MERCHANT_OWNER', 'ADMIN', 'USER_REPORT', 'SYSTEM'
        )
    ),
    CONSTRAINT ck_place_information_summary_last_verified CHECK (
        (last_verified_evidence_id IS NULL AND last_verified_source_type IS NULL AND last_verified_at IS NULL)
        OR (last_verified_evidence_id IS NOT NULL AND last_verified_source_type IS NOT NULL
            AND last_verified_at IS NOT NULL)
    )
);

-- Existing verified evidence becomes the initial summary without changing legacy place data.
INSERT INTO place_information_verification_summary (
    map_place_id,
    verified_evidence_count,
    last_verified_evidence_id,
    last_verified_source_type,
    last_verified_at,
    updated_at
)
SELECT
    e.map_place_id,
    COUNT(*)::INTEGER,
    (ARRAY_AGG(e.place_information_evidence_id ORDER BY e.reviewed_at DESC NULLS LAST,
        e.place_information_evidence_id DESC))[1],
    (ARRAY_AGG(e.source_type ORDER BY e.reviewed_at DESC NULLS LAST,
        e.place_information_evidence_id DESC))[1],
    MAX(e.reviewed_at),
    CURRENT_TIMESTAMP
FROM place_information_evidence e
WHERE e.verification_status IN ('SOURCE_CONFIRMED', 'ADMIN_VERIFIED')
GROUP BY e.map_place_id;

INSERT INTO place_information_verification_summary (map_place_id, updated_at)
SELECT p.map_place_id, CURRENT_TIMESTAMP
FROM map_place p
WHERE NOT EXISTS (
    SELECT 1
    FROM place_information_verification_summary s
    WHERE s.map_place_id = p.map_place_id
);

CREATE INDEX idx_place_information_summary_last_verified
    ON place_information_verification_summary (last_verified_at DESC NULLS LAST, map_place_id);
