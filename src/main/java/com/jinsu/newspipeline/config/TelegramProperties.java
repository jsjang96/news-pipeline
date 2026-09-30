package com.jinsu.newspipeline.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * application.yml 의 telegram.* 값. 봇 토큰/chat_id는 환경변수로만 주입(하드코딩 금지).
 */
@ConfigurationProperties(prefix = "telegram")
public record TelegramProperties(
        String botToken,
        String chatId,
        String baseUrl,
        int maxPerRun) {

    /** 토큰과 chat_id가 모두 있어야 전송 활성화. 없으면 전송 단계를 건너뛴다. */
    public boolean enabled() {
        return botToken != null && !botToken.isBlank()
                && chatId != null && !chatId.isBlank();
    }
}
