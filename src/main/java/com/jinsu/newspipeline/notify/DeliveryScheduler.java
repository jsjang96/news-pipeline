package com.jinsu.newspipeline.notify;

import com.jinsu.newspipeline.api.ArticleView;
import com.jinsu.newspipeline.config.TelegramProperties;
import com.jinsu.newspipeline.domain.Category;
import com.jinsu.newspipeline.repository.ArticleSummaryRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 하루 1번(기본 08:00) 아직 안 보낸 요약을 "다이제스트"로 묶어 텔레그램에 보낸다.
 *
 * 왜 묶나: 기사 1건당 1메시지로 보내면 하루치 수십 건이 알림 폭탄이 되고,
 * 텔레그램의 분당 전송 제한(그룹 약 20건)에도 걸린다.
 * 그래서 카테고리별로 묶어 메시지 수를 줄이고, 한 메시지 상한(4096자)에 맞춰 나눈다.
 *
 * 멱등성: 전송에 성공한 메시지에 담긴 기사만 notified_at 을 찍는다.
 * 실패한 메시지의 기사들은 notified_at 이 NULL로 남아 다음 날 다시 시도된다(실패 격리).
 */
@Component
public class DeliveryScheduler {

    private static final Logger log = LoggerFactory.getLogger(DeliveryScheduler.class);

    /** 텔레그램 한 메시지 상한은 4096자. 여유를 두고 자른다. */
    private static final int MAX_MESSAGE_CHARS = 3500;
    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private final TelegramClient telegramClient;
    private final TelegramProperties props;
    private final ArticleSummaryRepository repository;

    public DeliveryScheduler(TelegramClient telegramClient, TelegramProperties props,
                             ArticleSummaryRepository repository) {
        this.telegramClient = telegramClient;
        this.props = props;
        this.repository = repository;
    }

    @Scheduled(cron = "${telegram.cron:0 0 8 * * *}", zone = "Asia/Seoul")
    public void deliver() {
        if (!props.enabled()) {
            log.warn("TELEGRAM_BOT_TOKEN/CHAT_ID 미설정 → 전송 스킵");
            return;
        }

        List<ArticleView> unsent = repository.findUnsent(PageRequest.of(0, props.maxPerRun()));
        if (unsent.isEmpty()) {
            log.info("전송할 새 요약 없음 → 다이제스트 생략");
            return;
        }

        List<DigestMessage> messages = buildDigest(unsent);

        List<Long> sentIds = new ArrayList<>();
        for (DigestMessage m : messages) {
            try {
                telegramClient.sendMessage(m.text());
                sentIds.addAll(m.summaryIds());
            } catch (Exception e) {
                log.warn("다이제스트 전송 실패 (기사 {}건): {}", m.summaryIds().size(), e.toString());
            }
        }

        if (!sentIds.isEmpty()) {
            repository.markNotified(sentIds, Instant.now());
        }
        log.info("텔레그램 다이제스트: 메시지 {}개 / 기사 {}건 전송 (대상 {}건)",
                messages.size(), sentIds.size(), unsent.size());
    }

    /**
     * 카테고리별로 묶고, 메시지 길이 상한에 맞춰 나눈다.
     * 각 메시지는 "그 메시지에 담긴 기사 id 목록"을 함께 들고 있어서,
     * 전송 성공한 메시지의 기사만 골라 notified_at 을 찍을 수 있다.
     */
    private List<DigestMessage> buildDigest(List<ArticleView> articles) {
        Map<Category, List<ArticleView>> byCategory = new EnumMap<>(Category.class);
        for (ArticleView v : articles) {
            byCategory.computeIfAbsent(v.category(), k -> new ArrayList<>()).add(v);
        }

        String header = "📰 오늘의 IT 뉴스 ("
                + LocalDate.now(ZONE).format(DateTimeFormatter.ofPattern("M/d"))
                + ") · 총 " + articles.size() + "건";

        List<DigestMessage> messages = new ArrayList<>();
        StringBuilder body = new StringBuilder(header);
        List<Long> ids = new ArrayList<>();

        for (Map.Entry<Category, List<ArticleView>> entry : byCategory.entrySet()) {
            String section = "\n\n【" + entry.getKey().getLabel() + "】 " + entry.getValue().size() + "건";
            boolean firstOfSection = true;

            for (ArticleView v : entry.getValue()) {
                String article = "\n\n• " + v.title() + "\n" + v.summary() + "\n🔗 " + v.url();
                String block = firstOfSection ? section + article : article;

                // 상한을 넘으면 지금까지를 한 메시지로 끊고, 새 메시지에 섹션 머리말을 다시 단다
                if (body.length() + block.length() > MAX_MESSAGE_CHARS && !ids.isEmpty()) {
                    messages.add(new DigestMessage(body.toString(), List.copyOf(ids)));
                    ids.clear();
                    body = new StringBuilder("📰 (이어서)" + section);
                    block = article;
                }

                body.append(block);
                ids.add(v.id());
                firstOfSection = false;
            }
        }

        if (!ids.isEmpty()) {
            messages.add(new DigestMessage(body.toString(), List.copyOf(ids)));
        }
        return messages;
    }

    /** 한 번에 보낼 메시지 1개와, 거기 담긴 기사 id들. */
    private record DigestMessage(String text, List<Long> summaryIds) {
    }
}
