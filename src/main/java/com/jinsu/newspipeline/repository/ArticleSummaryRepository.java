package com.jinsu.newspipeline.repository;

import com.jinsu.newspipeline.api.ArticleView;
import com.jinsu.newspipeline.domain.ArticleSummary;
import com.jinsu.newspipeline.domain.Category;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface ArticleSummaryRepository extends JpaRepository<ArticleSummary, Long> {

    /** 요약 + 원본(raw_article)을 조인해 화면용 DTO로 조회. category가 null이면 전체. */
    @Query("""
            select new com.jinsu.newspipeline.api.ArticleView(
                s.id, r.title, r.url, r.source, s.category, s.summary, s.createdAt)
            from ArticleSummary s
            join RawArticle r on r.id = s.rawArticleId
            where (:category is null or s.category = :category)
            order by s.id desc
            """)
    java.util.List<ArticleView> findViews(@Param("category") Category category, Pageable pageable);

    @Query("""
            select new com.jinsu.newspipeline.api.ArticleView(
                s.id, r.title, r.url, r.source, s.category, s.summary, s.createdAt)
            from ArticleSummary s
            join RawArticle r on r.id = s.rawArticleId
            where s.id = :id
            """)
    Optional<ArticleView> findViewById(@Param("id") Long id);

    /** 아직 안 보낸(notified_at IS NULL) 요약을 오래된 순으로 조회 (텔레그램 전송용). */
    @Query("""
            select new com.jinsu.newspipeline.api.ArticleView(
                s.id, r.title, r.url, r.source, s.category, s.summary, s.createdAt)
            from ArticleSummary s
            join RawArticle r on r.id = s.rawArticleId
            where s.notifiedAt is null
            order by s.id asc
            """)
    List<ArticleView> findUnsent(Pageable pageable);

    /** 전송 성공한 요약들의 notified_at 을 한 번에 기록. */
    @Transactional
    @Modifying
    @Query("update ArticleSummary s set s.notifiedAt = :now where s.id in :ids")
    int markNotified(@Param("ids") List<Long> ids, @Param("now") Instant now);
}
