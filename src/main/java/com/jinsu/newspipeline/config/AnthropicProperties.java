package com.jinsu.newspipeline.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * application.yml 의 anthropic.* 값. API 키는 환경변수로만 주입한다(하드코딩 금지).
 */
@ConfigurationProperties(prefix = "anthropic")
public record AnthropicProperties(
        String apiKey,
        String baseUrl,
        String model,
        int maxTokens) {

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }
}
