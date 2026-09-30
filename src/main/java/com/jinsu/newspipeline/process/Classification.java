package com.jinsu.newspipeline.process;

import com.jinsu.newspipeline.domain.Category;

/**
 * LLM 응답을 파싱한 결과: 카테고리 + 요약문.
 */
public record Classification(Category category, String summary) {
}
