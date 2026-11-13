-- 구버전 User writer는 version을 증가시키지 않으므로 전체 writer를 교체한 뒤 동시성 보호를 보장한다.
ALTER TABLE users ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
