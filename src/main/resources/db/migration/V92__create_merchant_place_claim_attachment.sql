CREATE TABLE merchant_place_claim_attachment (
    id BIGSERIAL PRIMARY KEY,
    claim_id BIGINT NOT NULL,
    document_type VARCHAR(30) NOT NULL,
    storage_key VARCHAR(500) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    file_size BIGINT NOT NULL,
    file_hash VARCHAR(64) NOT NULL,
    display_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_claim_attachment_claim
        FOREIGN KEY (claim_id) REFERENCES merchant_place_claim (id) ON DELETE CASCADE,
    CONSTRAINT ck_claim_attachment_document_type
        CHECK (document_type IN ('BUSINESS_LICENSE', 'RESIDENT_REGISTRATION', 'REPRESENTATIVE_IMAGE')),
    CONSTRAINT ck_claim_attachment_file_size
        CHECK (file_size > 0),
    CONSTRAINT ck_claim_attachment_display_order
        CHECK (display_order >= 0)
);

CREATE INDEX idx_claim_attachment_claim_type_order
    ON merchant_place_claim_attachment (claim_id, document_type, display_order, id);

CREATE UNIQUE INDEX uq_claim_attachment_sensitive_document
    ON merchant_place_claim_attachment (claim_id, document_type)
    WHERE document_type IN ('BUSINESS_LICENSE', 'RESIDENT_REGISTRATION');

CREATE UNIQUE INDEX uq_claim_attachment_hash
    ON merchant_place_claim_attachment (claim_id, document_type, file_hash);
