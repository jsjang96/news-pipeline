package com.jinsu.newspipeline.collect;

import com.jinsu.newspipeline.domain.RawArticle;
import com.jinsu.newspipeline.repository.RawArticleRepository;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.stereotype.Component;

/**
 * FeedItem -> RawArticle(PENDING) 변환. 이미 수집한 URL이면 null을 반환해
 * Writer로 넘기지 않는다(중복 저장 방지).
 */
@Component
public class RawArticleItemProcessor implements ItemProcessor<FeedItem, RawArticle> {

    private final RawArticleRepository repository;

    public RawArticleItemProcessor(RawArticleRepository repository) {
        this.repository = repository;
    }

    @Override
    public RawArticle process(FeedItem item) {
        if (repository.existsByUrl(item.url())) {
            return null; // 이미 있는 기사 -> 스킵
        }
        return RawArticle.pending(item.title(), item.url(), item.content(), item.source());
    }
}
