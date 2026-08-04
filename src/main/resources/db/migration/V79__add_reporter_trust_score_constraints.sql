ALTER TABLE reporter_moderation_policy
    ADD CONSTRAINT ck_reporter_moderation_policy_counts
        CHECK (
            submitted_count >= 0
            AND accepted_count >= 0
            AND declined_count >= 0
            AND false_report_count >= 0
            AND accepted_count + declined_count <= submitted_count
            AND false_report_count <= declined_count
        ),
    ADD CONSTRAINT ck_reporter_moderation_policy_trust_score
        CHECK (trust_score BETWEEN 0 AND 100);
