package com.jinsu.newspipeline.collect;

import com.jinsu.newspipeline.config.RssProperties;
import java.util.Iterator;
import java.util.List;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.infrastructure.item.ItemReader;
import org.springframework.stereotype.Component;

/**
 * 수집 Step의 Reader. Step 실행마다 새 인스턴스(@StepScope)라서,
 * 첫 read() 호출 때 피드를 한 번 받아오고 이후엔 항목을 하나씩 내보낸다.
 * 더 읽을 게 없으면 null을 반환해 Step 종료를 알린다.
 */
@Component
@StepScope
public class RssItemReader implements ItemReader<FeedItem> {

    private final RssClient rssClient;
    private final RssProperties rssProperties;

    private Iterator<FeedItem> iterator;

    public RssItemReader(RssClient rssClient, RssProperties rssProperties) {
        this.rssClient = rssClient;
        this.rssProperties = rssProperties;
    }

    @Override
    public FeedItem read() {
        if (iterator == null) {
            List<FeedItem> items = rssClient.fetch(rssProperties.url(), rssProperties.source());
            iterator = items.iterator();
        }
        return iterator.hasNext() ? iterator.next() : null;
    }
}
