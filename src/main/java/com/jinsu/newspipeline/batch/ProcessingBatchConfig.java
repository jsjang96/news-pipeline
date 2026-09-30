package com.jinsu.newspipeline.batch;

import com.jinsu.newspipeline.domain.RawArticle;
import com.jinsu.newspipeline.process.PendingArticleReader;
import com.jinsu.newspipeline.process.ProcessedArticle;
import com.jinsu.newspipeline.process.ProcessedArticleWriter;
import com.jinsu.newspipeline.process.SummaryProcessor;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * v1 처리 배치: PENDING 기사 -> Claude 요약·분류 -> article_summary 저장 + 상태 DONE/FAILED.
 * chunk를 작게(5) 잡아 API 호출 진행 상황을 자주 커밋한다(중간에 죽어도 손실 최소화).
 */
@Configuration
public class ProcessingBatchConfig {

    private static final int CHUNK_SIZE = 5;

    @Bean
    public Job processingJob(JobRepository jobRepository, Step processingStep) {
        return new JobBuilder("processingJob", jobRepository)
                .start(processingStep)
                .build();
    }

    @Bean
    public Step processingStep(JobRepository jobRepository,
                               PlatformTransactionManager transactionManager,
                               PendingArticleReader reader,
                               SummaryProcessor processor,
                               ProcessedArticleWriter writer) {
        return new StepBuilder("processingStep", jobRepository)
                .<RawArticle, ProcessedArticle>chunk(CHUNK_SIZE, transactionManager)
                .reader(reader)
                .processor(processor)
                .writer(writer)
                .build();
    }
}
