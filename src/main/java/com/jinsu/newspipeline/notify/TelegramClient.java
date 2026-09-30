package com.jinsu.newspipeline.notify;

import com.jinsu.newspipeline.config.TelegramProperties;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Telegram Bot API로 메시지를 보낸다(WebClient 직접 호출 — 학습 목적 일관성).
 * 엔드포인트: POST https://api.telegram.org/bot<TOKEN>/sendMessage
 */
@Component
public class TelegramClient {

    private final WebClient webClient = WebClient.builder().build();
    private final TelegramProperties props;

    public TelegramClient(TelegramProperties props) {
        this.props = props;
    }

    public void sendMessage(String text) {
        // 토큰에 ':'가 들어가 URI 템플릿 인코딩되면 안 되므로 전체 URI를 직접 조립한다.
        URI uri = URI.create(props.baseUrl() + "/bot" + props.botToken() + "/sendMessage");

        Map<String, Object> body = Map.of(
                "chat_id", props.chatId(),
                "text", text,
                "disable_web_page_preview", true);

        webClient.post()
                .uri(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .toBodilessEntity()
                .block(Duration.ofSeconds(15));
    }
}
