-- 텔레그램 전송 여부 추적 컬럼 (v1.5 전송 단계)
-- notified_at IS NULL = 아직 안 보낸 요약. 보낸 뒤 시각을 기록해 중복 전송을 막는다(멱등성).
ALTER TABLE article_summary ADD COLUMN notified_at TIMESTAMPTZ;

-- 미전송 건만 빠르게 찾기 위한 부분 인덱스
CREATE INDEX idx_article_summary_unsent ON article_summary (id) WHERE notified_at IS NULL;
