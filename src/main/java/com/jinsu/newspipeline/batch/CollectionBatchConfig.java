package com.jinsu.newspipeline.batch;

import com.jinsu.newspipeline.collect.FeedItem;
import com.jinsu.newspipeline.collect.RawArticleItemProcessor;
import com.jinsu.newspipeline.collect.RawArticleItemWriter;
import com.jinsu.newspipeline.collect.RssItemReader;
import com.jinsu.newspipeline.domain.RawArticle;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * v0 수집 배치: RSS -> raw_article(PENDING).
 * Reader(피드 읽기) -> Processor(중복 스킵 + 엔티티 변환) -> Writer(저장) 를 chunk 단위로 처리.
 */
@Configuration
public class CollectionBatchConfig {

    private static final int CHUNK_SIZE = 20;

    @Bean
    public Job collectionJob(JobRepository jobRepository, Step collectionStep) {
        return new JobBuilder("collectionJob", jobRepository)
                .start(collectionStep)
                .build();
    }

    @Bean
    public Step collectionStep(JobRepository jobRepository,
                               PlatformTransactionManager transactionManager,
                               RssItemReader reader,
                               RawArticleItemProcessor processor,
                               RawArticleItemWriter writer) {
        return new StepBuilder("collectionStep", jobRepository)
                .<FeedItem, RawArticle>chunk(CHUNK_SIZE, transactionManager)
                .reader(reader)
                .processor(processor)
                .writer(writer)
                .build();
    }
}
