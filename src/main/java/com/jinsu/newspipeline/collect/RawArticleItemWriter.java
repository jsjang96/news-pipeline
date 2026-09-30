package com.jinsu.newspipeline.collect;

import com.jinsu.newspipeline.domain.RawArticle;
import com.jinsu.newspipeline.repository.RawArticleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.stereotype.Component;

/**
 * chunk 단위로 넘어온 신규 기사들을 한 트랜잭션에 저장한다.
 */
@Component
public class RawArticleItemWriter implements ItemWriter<RawArticle> {

    private static final Logger log = LoggerFactory.getLogger(RawArticleItemWriter.class);

    private final RawArticleRepository repository;

    public RawArticleItemWriter(RawArticleRepository repository) {
        this.repository = repository;
    }

    @Override
    public void write(Chunk<? extends RawArticle> chunk) {
        if (chunk.isEmpty()) {
            return;
        }
        repository.saveAll(chunk.getItems());
        log.debug("신규 기사 {}건 저장", chunk.size());
    }
}
