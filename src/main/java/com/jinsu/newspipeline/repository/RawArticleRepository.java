package com.jinsu.newspipeline.repository;

import com.jinsu.newspipeline.domain.ArticleStatus;
import com.jinsu.newspipeline.domain.RawArticle;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RawArticleRepository extends JpaRepository<RawArticle, Long> {

    /** 이미 수집한 기사인지 URL로 중복 확인 (수집 단계 dedupe). */
    boolean existsByUrl(String url);

    /**
     * 처리 배치 Reader용 커서 조회: 지정 상태이면서 id가 lastId보다 큰 것만 상위 50건.
     * "id > lastId" 조건 덕분에 한 번 읽어 넘긴 기사는(아직 상태가 안 바뀌었어도) 다시 안 읽힌다.
     */
    List<RawArticle> findTop50ByStatusAndIdGreaterThanOrderByIdAsc(ArticleStatus status, Long id);
}
