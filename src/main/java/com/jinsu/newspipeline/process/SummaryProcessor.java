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
    private final SummaryCache cache;
    private final AnthropicProperties props;

    public SummaryProcessor(ClaudeClient claudeClient, SummaryCache cache, AnthropicProperties props) {
        this.claudeClient = claudeClient;
        this.cache = cache;
        this.props = props;
    }

    @Override
    public ProcessedArticle process(RawArticle article) {
        String title = article.getTitle();
        String content = article.getContent();
        try {
            // 1) Redis 캐시 확인 — 같은 본문을 이미 요약했으면 Claude 호출 스킵(비용 절감)
            Classification c = cache.get(title, content).orElse(null);
            boolean fromCache = (c != null);

            // 2) 없으면 Claude 호출 후 캐시에 저장
            if (c == null) {
                c = claudeClient.summarizeAndClassify(title, content);
                cache.put(title, content, c);
            }

            ArticleSummary summary = ArticleSummary.of(article.getId(), c.category(), c.summary(), props.model());
            article.setStatus(ArticleStatus.DONE);
            log.debug("요약 완료 id={} category={} ({})",
                    article.getId(), c.category(), fromCache ? "캐시" : "API");
            return new ProcessedArticle(article, summary);
        } catch (Exception e) {
            article.setStatus(ArticleStatus.FAILED);
            log.warn("요약 실패 id={} -> FAILED: {}", article.getId(), e.toString());
            return new ProcessedArticle(article, null);
        }
    }
}
