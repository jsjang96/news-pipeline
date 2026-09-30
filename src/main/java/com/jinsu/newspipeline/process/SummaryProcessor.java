package com.jinsu.newspipeline.process;

import com.jinsu.newspipeline.config.AnthropicProperties;
import com.jinsu.newspipeline.domain.ArticleStatus;
import com.jinsu.newspipeline.domain.ArticleSummary;
import com.jinsu.newspipeline.domain.RawArticle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.stereotype.Component;

/**
 * 기사 1건을 Claude로 요약·분류한다.
 * 성공 → 요약 생성 + 상태 DONE. 실패 → 상태 FAILED(요약 null)로 격리하고 계속 진행
 * (한 건 실패가 배치 전체를 멈추지 않게).
 */
@Component
public class SummaryProcessor implements ItemProcessor<RawArticle, ProcessedArticle> {

    private static final Logger log = LoggerFactory.getLogger(SummaryProcessor.class);

    private final ClaudeClient claudeClient;
    private final AnthropicProperties props;

    public SummaryProcessor(ClaudeClient claudeClient, AnthropicProperties props) {
        this.claudeClient = claudeClient;
        this.props = props;
    }

    @Override
    public ProcessedArticle process(RawArticle article) {
        try {
            Classification c = claudeClient.summarizeAndClassify(article.getTitle(), article.getContent());
            ArticleSummary summary = ArticleSummary.of(article.getId(), c.category(), c.summary(), props.model());
            article.setStatus(ArticleStatus.DONE);
            log.debug("요약 완료 id={} category={}", article.getId(), c.category());
            return new ProcessedArticle(article, summary);
        } catch (Exception e) {
            article.setStatus(ArticleStatus.FAILED);
            log.warn("요약 실패 id={} -> FAILED: {}", article.getId(), e.toString());
            return new ProcessedArticle(article, null);
        }
    }
}
