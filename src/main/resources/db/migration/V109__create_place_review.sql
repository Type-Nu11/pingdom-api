CREATE TABLE place_review (id BIGSERIAL PRIMARY KEY, place_id BIGINT NOT NULL REFERENCES map_place(map_place_id), user_id BIGINT NOT NULL, recommend_reason VARCHAR(100) NOT NULL, content VARCHAR(2000) NOT NULL, created_at TIMESTAMP NOT NULL);
CREATE TABLE place_review_image (review_id BIGINT NOT NULL REFERENCES place_review(id) ON DELETE CASCADE, image_url VARCHAR(500) NOT NULL);
CREATE INDEX idx_place_review_place_created_at ON place_review(place_id, created_at DESC);
