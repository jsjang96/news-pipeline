package com.jinsu.newspipeline.collect;

import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * RSS URL을 HTTP로 받아 Rome으로 파싱한다.
 * JDK HttpClient로 직접 호출해서 User-Agent 등 헤더를 제어한다(일부 소스가 UA 없는 요청을 막음).
 */
@Component
public class RssClient {

    private static final Logger log = LoggerFactory.getLogger(RssClient.class);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public List<FeedItem> fetch(String url, String source) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .header("User-Agent", "news-pipeline/0.0.1 (personal learning project)")
                    .header("Accept", "application/rss+xml, application/xml, text/xml;q=0.9, */*;q=0.8")
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();

            HttpResponse<InputStream> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

            try (InputStream body = response.body()) {
                if (response.statusCode() != 200) {
                    throw new IllegalStateException("RSS fetch failed: HTTP " + response.statusCode() + " for " + response.uri());
                }

                SyndFeed feed = new SyndFeedInput().build(new XmlReader(body));
                List<FeedItem> items = new ArrayList<>();
                for (SyndEntry entry : feed.getEntries()) {
                    String link = entry.getLink();
                    if (link == null || link.isBlank()) {
                        continue; // 링크(고유 식별자로 씀) 없는 항목은 스킵
                    }
                    items.add(new FeedItem(
                            safeTitle(entry),
                            link.trim(),
                            extractContent(entry),
                            source));
                }
                log.debug("RSS 파싱 완료: {}건 ({})", items.size(), url);
                return items;
            }
        } catch (Exception e) {
            throw new RssFetchException("RSS 수집/파싱 실패: " + url, e);
        }
    }

    private String safeTitle(SyndEntry entry) {
        String title = entry.getTitle();
        return (title == null || title.isBlank()) ? "(제목 없음)" : title.trim();
    }

    private String extractContent(SyndEntry entry) {
        if (entry.getContents() != null && !entry.getContents().isEmpty()) {
            String value = entry.getContents().get(0).getValue();
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        if (entry.getDescription() != null && entry.getDescription().getValue() != null) {
            return entry.getDescription().getValue().trim();
        }
        return null;
    }

    /** 수집 실패를 명확히 구분하기 위한 런타임 예외. */
    public static class RssFetchException extends RuntimeException {
        public RssFetchException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
