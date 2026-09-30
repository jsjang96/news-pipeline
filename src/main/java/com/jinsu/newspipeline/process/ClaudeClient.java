package com.jinsu.newspipeline.process;

import com.jinsu.newspipeline.config.AnthropicProperties;
import com.jinsu.newspipeline.domain.Category;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Claude Messages API를 WebClient로 직접 호출한다(학습 목적 — SDK 미사용).
 * 기사 하나를 받아 "카테고리 + 요약"을 돌려준다.
 */
@Component
public class ClaudeClient {

    private static final Logger log = LoggerFactory.getLogger(ClaudeClient.class);
    private static final int MAX_CONTENT_CHARS = 4000; // 프롬프트 폭주(비용) 방지

    private final WebClient webClient;
    private final AnthropicProperties props;

    public ClaudeClient(AnthropicProperties props) {
        this.props = props;
        this.webClient = WebClient.builder()
                .baseUrl(props.baseUrl())
                .build();
    }

    public Classification summarizeAndClassify(String title, String content) {
        String prompt = buildPrompt(title, content);

        Map<String, Object> body = Map.of(
                "model", props.model(),
                "max_tokens", props.maxTokens(),
                "messages", List.of(Map.of("role", "user", "content", prompt)));

        AnthropicResponse res = webClient.post()
                .uri("/v1/messages")
                .header("x-api-key", props.apiKey())
                .header("anthropic-version", "2023-06-01")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(AnthropicResponse.class)
                .block(Duration.ofSeconds(30));

        String text = (res == null) ? "" : res.firstText();
        return parse(text);
    }

    private String buildPrompt(String title, String content) {
        String safeContent = content == null ? "" : content;
        if (safeContent.length() > MAX_CONTENT_CHARS) {
            safeContent = safeContent.substring(0, MAX_CONTENT_CHARS);
        }
        return """
                당신은 IT 뉴스 분류·요약 도우미입니다.
                아래 기사를 읽고 정확히 다음 두 줄 형식으로만 답하세요. 다른 말은 절대 하지 마세요.

                CATEGORY: <AI, 개발, 클라우드·인프라, 보안, 기타 중 정확히 하나. 위 4개에 명확히 안 맞으면 기타>
                SUMMARY: <한국어 2~3문장 요약>

                제목: %s
                본문: %s
                """.formatted(title, safeContent);
    }

    /** "CATEGORY: ...", "SUMMARY: ..." 두 줄 형식을 파싱. 형식이 깨져도 최대한 복구. */
    private Classification parse(String text) {
        String categoryRaw = null;
        StringBuilder summary = new StringBuilder();
        boolean inSummary = false;

        for (String line : text.split("\\R")) {
            String t = line.strip();
            if (t.regionMatches(true, 0, "CATEGORY:", 0, "CATEGORY:".length())) {
                categoryRaw = t.substring("CATEGORY:".length()).strip();
                inSummary = false;
            } else if (t.regionMatches(true, 0, "SUMMARY:", 0, "SUMMARY:".length())) {
                summary.append(t.substring("SUMMARY:".length()).strip());
                inSummary = true;
            } else if (inSummary && !t.isEmpty()) {
                summary.append(' ').append(t);
            }
        }

        Category category = Category.fromLabel(categoryRaw);
        String sum = summary.toString().strip();
        if (sum.isEmpty()) {
            log.warn("SUMMARY 파싱 실패, 원문으로 대체. 응답='{}'", text);
            sum = text.strip();
        }
        return new Classification(category, sum);
    }

    /** Anthropic Messages API 응답 중 필요한 부분만 매핑(나머지 필드는 무시). */
    public record AnthropicResponse(List<Block> content) {
        public record Block(String type, String text) {
        }

        public String firstText() {
            if (content == null) {
                return "";
            }
            return content.stream()
                    .filter(b -> "text".equals(b.type()))
                    .map(Block::text)
                    .findFirst()
                    .orElse("");
        }
    }
}
