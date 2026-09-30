package com.jinsu.newspipeline.process;

import com.jinsu.newspipeline.domain.ArticleStatus;
import com.jinsu.newspipeline.domain.RawArticle;
import com.jinsu.newspipeline.repository.RawArticleRepository;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.infrastructure.item.ItemReader;
import org.springframework.stereotype.Component;

/**
 * PENDING 기사를 읽는 Reader (커서/keyset 방식).
 *
 * 버퍼가 비면 "status=PENDING AND id > lastId"로 다음 50건을 조회한다.
 * 핵심: 기사를 read()로 넘긴 순간엔 아직 처리 전이라 상태가 PENDING 그대로다.
 * 만약 "PENDING 상위 50건"만으로 재조회하면 방금 넘긴(아직 커밋 안 된) 기사가 다시 딸려와
 * 한 청크에서 같은 기사를 두 번 처리 → 요약 중복 INSERT(유니크 제약 위반)가 난다.
 * lastId 커서를 두어 "이미 넘긴 id는 두 번 다시 안 읽는다"로 이 문제를 원천 차단한다.
 * (@StepScope 라 Step 실행마다 lastId가 0으로 초기화된다)
 */
@Component
@StepScope
public class PendingArticleReader implements ItemReader<RawArticle> {

    private final RawArticleRepository repository;
    private final Deque<RawArticle> buffer = new ArrayDeque<>();

    private long lastId = 0L;

    public PendingArticleReader(RawArticleRepository repository) {
        this.repository = repository;
    }

    @Override
    public RawArticle read() {
        if (buffer.isEmpty()) {
            List<RawArticle> next =
                    repository.findTop50ByStatusAndIdGreaterThanOrderByIdAsc(ArticleStatus.PENDING, lastId);
            buffer.addAll(next);
        }
        RawArticle article = buffer.poll();
        if (article != null) {
            lastId = article.getId(); // 커서 전진 — 이 id 이하는 다시 조회하지 않음
        }
        return article;
    }
}
