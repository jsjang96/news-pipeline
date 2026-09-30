package com.jinsu.newspipeline.batch;

import com.jinsu.newspipeline.config.AnthropicProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 처리 Job을 주기적으로(기본 10분) 실행한다.
 * API 키가 없으면 401 도배로 기사들이 헛되이 FAILED 되는 걸 막기 위해 실행 자체를 건너뛴다.
 */
@Component
public class ProcessingScheduler {

    private static final Logger log = LoggerFactory.getLogger(ProcessingScheduler.class);

    private final JobOperator jobOperator;
    private final Job processingJob;
    private final AnthropicProperties anthropicProperties;

    public ProcessingScheduler(JobOperator jobOperator, Job processingJob,
                               AnthropicProperties anthropicProperties) {
        this.jobOperator = jobOperator;
        this.processingJob = processingJob;
        this.anthropicProperties = anthropicProperties;
    }

    @Scheduled(initialDelay = 15_000, fixedDelayString = "${news.processing.interval-ms:600000}")
    public void runProcessing() {
        if (!anthropicProperties.hasApiKey()) {
            log.warn("ANTHROPIC_API_KEY 미설정 → 처리 배치 스킵 (환경변수 설정 후 재시작하세요)");
            return;
        }
        try {
            JobParameters parameters = new JobParametersBuilder()
                    .addLong("run.id", System.currentTimeMillis())
                    .toJobParameters();

            var execution = jobOperator.start(processingJob, parameters);
            log.info("processingJob 종료: status={}, 읽음={}, 쓴건={}",
                    execution.getStatus(),
                    execution.getStepExecutions().stream().mapToInt(s -> (int) s.getReadCount()).sum(),
                    execution.getStepExecutions().stream().mapToInt(s -> (int) s.getWriteCount()).sum());
        } catch (Exception e) {
            log.error("processingJob 실행 실패", e);
        }
    }
}
