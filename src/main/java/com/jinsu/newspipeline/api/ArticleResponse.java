package com.jinsu.newspipeline.api;

import java.time.Instant;

/**
 * REST 응답 DTO. category는 한국어 라벨로 내려준다.
 */
public record ArticleResponse(
        Long id,
        String title,
        String url,
        String source,
        String category,
        String summary,
        Instant createdAt) {

    public static ArticleResponse from(ArticleView v) {
        return new ArticleResponse(
                v.id(),
                v.title(),
                v.url(),
                v.source(),
                v.category().getLabel(),
                v.summary(),
                v.createdAt());
    }
}
