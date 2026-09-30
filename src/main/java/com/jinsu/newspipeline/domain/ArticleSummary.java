package com.jinsu.newspipeline.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;

/**
 * 처리 배치(v1)가 만든 요약·분류 결과. raw_article 1건당 1건.
 */
@Entity
@Table(name = "article_summary")
public class ArticleSummary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "raw_article_id", nullable = false)
    private Long rawArticleId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Category category;

    @Column(nullable = false, columnDefinition = "text")
    private String summary;

    @Column(nullable = false, length = 50)
    private String model;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** 텔레그램 전송 시각. NULL이면 아직 안 보낸 것(전송 단계에서 사용). */
    @Column(name = "notified_at")
    private Instant notifiedAt;

    protected ArticleSummary() {
        // JPA 기본 생성자
    }

    public static ArticleSummary of(Long rawArticleId, Category category, String summary, String model) {
        ArticleSummary s = new ArticleSummary();
        s.rawArticleId = rawArticleId;
        s.category = category;
        s.summary = summary;
        s.model = model;
        return s;
    }

    public Long getId() {
        return id;
    }

    public Long getRawArticleId() {
        return rawArticleId;
    }

    public Category getCategory() {
        return category;
    }

    public String getSummary() {
        return summary;
    }

    public String getModel() {
        return model;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getNotifiedAt() {
        return notifiedAt;
    }
}
