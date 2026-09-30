package com.jinsu.newspipeline.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * application.yml 의 news.rss.* 값을 담는다.
 * v0는 단일 소스(GeekNews). v1 이후 소스를 리스트로 확장할 수 있다.
 */
@ConfigurationProperties(prefix = "news.rss")
public record RssProperties(String source, String url) {
}
