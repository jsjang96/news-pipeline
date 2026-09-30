package com.jinsu.newspipeline.api;

import com.jinsu.newspipeline.domain.Category;
import java.time.Instant;

/**
 * 조회 쿼리(article_summary + raw_article 조인) 결과 투영용 DTO.
 */
public record ArticleView(
        Long id,
        String title,
        String url,
        String source,
        Category category,
        String summary,
        Instant createdAt) {
}
