package com.jinsu.newspipeline.collect;

/**
 * RSS 피드에서 뽑아낸 한 건. 아직 DB 엔티티가 아니라 수집 파이프라인 내부 DTO.
 */
public record FeedItem(String title, String url, String content, String source) {
}
