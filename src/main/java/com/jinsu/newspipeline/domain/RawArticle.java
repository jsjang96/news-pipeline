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
 * RSS로 수집한 원본 기사. 스키마는 Flyway(V1)가 만들고, 이 엔티티는 그 테이블에 매핑된다.
 */
@Entity
@Table(name = "raw_article")
public class RawArticle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 1000)
    private String title;

    @Column(nullable = false, unique = true, length = 1000)
    private String url;

    @Column(columnDefinition = "text")
    private String content;

    @Column(nullable = false, length = 100)
    private String source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ArticleStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected RawArticle() {
        // JPA 기본 생성자
    }

    public static RawArticle pending(String title, String url, String content, String source) {
        RawArticle a = new RawArticle();
        a.title = title;
        a.url = url;
        a.content = content;
        a.source = source;
        a.status = ArticleStatus.PENDING;
        return a;
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getUrl() {
        return url;
    }

    public String getContent() {
        return content;
    }

    public String getSource() {
        return source;
    }

    public ArticleStatus getStatus() {
        return status;
    }

    public void setStatus(ArticleStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
