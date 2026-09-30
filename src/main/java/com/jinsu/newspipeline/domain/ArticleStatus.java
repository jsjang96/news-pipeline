package com.jinsu.newspipeline.domain;

/**
 * 기사 처리 상태. 배치가 중단됐다 재개돼도 상태로 진행 지점을 추적한다(멱등성).
 * PENDING(수집됨) -> PROCESSING(요약/분류 중) -> DONE(완료) / FAILED(실패, 재시도 대상)
 */
public enum ArticleStatus {
    PENDING,
    PROCESSING,
    DONE,
    FAILED
}
