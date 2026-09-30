package com.jinsu.newspipeline.process;

import com.jinsu.newspipeline.domain.ArticleSummary;
import com.jinsu.newspipeline.domain.RawArticle;

/**
 * Processor -> Writer로 넘기는 묶음.
 * article: 상태가 DONE/FAILED로 바뀐 원본. summary: 성공 시 요약(실패면 null).
 */
public record ProcessedArticle(RawArticle article, ArticleSummary summary) {
}
