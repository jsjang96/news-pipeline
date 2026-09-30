package com.jinsu.newspipeline.batch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 수집 Job을 주기적으로(기본 5분) 실행한다.
 * 앱 시작 5초 뒤 첫 실행 -> 이후 news.collection.interval-ms 간격.
 * 매 실행마다 고유 파라미터(run.id=현재시각)를 넣어 새로운 JobInstance로 돌게 한다.
 */
@Component
public class CollectionScheduler {

    private static final Logger log = LoggerFactory.getLogger(CollectionScheduler.class);

    private final JobOperator jobOperator;
    private final Job collectionJob;

    public CollectionScheduler(JobOperator jobOperator, Job collectionJob) {
        this.jobOperator = jobOperator;
        this.collectionJob = collectionJob;
    }

    @Scheduled(initialDelay = 5_000, fixedDelayString = "${news.collection.interval-ms:300000}")
    public void runCollection() {
        try {
            JobParameters parameters = new JobParametersBuilder()
                    .addLong("run.id", System.currentTimeMillis())
                    .toJobParameters();

            var execution = jobOperator.start(collectionJob, parameters);
            log.info("collectionJob 종료: status={}, 읽음={}, 쓴건={}",
                    execution.getStatus(),
                    execution.getStepExecutions().stream().mapToInt(s -> (int) s.getReadCount()).sum(),
                    execution.getStepExecutions().stream().mapToInt(s -> (int) s.getWriteCount()).sum());
        } catch (Exception e) {
            log.error("collectionJob 실행 실패", e);
        }
    }
}
