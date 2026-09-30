package com.jinsu.newspipeline.process;

import com.jinsu.newspipeline.repository.ArticleSummaryRepository;
import com.jinsu.newspipeline.repository.RawArticleRepository;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.stereotype.Component;

/**
 * 요약(있으면)과 원본 상태 변경을 한 트랜잭션(chunk)에 저장한다.
 * 원본 save는 기존 id라 merge(=상태 갱신)로 동작한다.
 */
@Component
public class ProcessedArticleWriter implements ItemWriter<ProcessedArticle> {

    private final RawArticleRepository rawArticleRepository;
    private final ArticleSummaryRepository summaryRepository;

    public ProcessedArticleWriter(RawArticleRepository rawArticleRepository,
                                  ArticleSummaryRepository summaryRepository) {
        this.rawArticleRepository = rawArticleRepository;
        this.summaryRepository = summaryRepository;
    }

    @Override
    public void write(Chunk<? extends ProcessedArticle> chunk) {
        for (ProcessedArticle p : chunk) {
            if (p.summary() != null) {
                summaryRepository.save(p.summary());
            }
            rawArticleRepository.save(p.article());
        }
    }
}
