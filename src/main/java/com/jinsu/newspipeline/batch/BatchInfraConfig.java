package com.jinsu.newspipeline.batch;

import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.configuration.annotation.EnableJdbcJobRepository;
import org.springframework.context.annotation.Configuration;

/**
 * 배치 인프라 설정.
 *
 * Spring Batch 6부터 JobRepository 기본값이 인메모리(ResourcelessJobRepository)로 바뀌었다.
 * 그 상태로는 실행 이력이 BATCH_* 테이블에 안 남아 재시작/재개(멱등성)를 확인할 수 없다.
 * 실행 이력을 DB에 영속화하려면 @EnableJdbcJobRepository 로 JDBC 백엔드를 명시적으로 켠다.
 * (@EnableBatchProcessing 이 있으면 Spring Boot의 기본 배치 자동설정은 물러난다.)
 *
 * dataSourceRef/transactionManagerRef 는 기본값("dataSource"/"transactionManager")을 써서
 * Boot이 만들어 준 빈을 그대로 재사용한다.
 */
@Configuration
@EnableBatchProcessing
@EnableJdbcJobRepository
public class BatchInfraConfig {
}
