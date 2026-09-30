package com.jinsu.newspipeline.notify;

import com.jinsu.newspipeline.api.ArticleView;
import com.jinsu.newspipeline.config.TelegramProperties;
import com.jinsu.newspipeline.repository.ArticleSummaryRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 아직 안 보낸(notified_at IS NULL) 요약을 텔레그램으로 전송한다.
 * 한 번에 max-per-run 건씩만 보내 백로그가 한꺼번에 쏟아지는 걸 막고,
 * 전송 성공한 것만 notified_at 을 찍어 중복 전송을 방지한다(멱등성).
 * 전송 실패 건은 notified_at 이 그대로 NULL이라 다음 주기에 재시도된다(실패 격리).
 */
@Component
public class DeliveryScheduler {

    private static final Logger log = LoggerFactory.getLogger(DeliveryScheduler.class);

    private final TelegramClient telegramClient;
    private final TelegramProperties props;
    private final ArticleSummaryRepository repository;

    public DeliveryScheduler(TelegramClient telegramClient, TelegramProperties props,
                             ArticleSummaryRepository repository) {
        this.telegramClient = telegramClient;
        this.props = props;
        this.repository = repository;
    }

    @Scheduled(initialDelay = 20_000, fixedDelayString = "${telegram.interval-ms:120000}")
    public void deliver() {
        if (!props.enabled()) {
            log.warn("TELEGRAM_BOT_TOKEN/CHAT_ID 미설정 → 전송 스킵");
            return;
        }

        List<ArticleView> unsent = repository.findUnsent(PageRequest.of(0, props.maxPerRun()));
        if (unsent.isEmpty()) {
            return;
        }

        List<Long> sentIds = new ArrayList<>();
        for (ArticleView v : unsent) {
            try {
                telegramClient.sendMessage(format(v));
                sentIds.add(v.id());
            } catch (Exception e) {
                log.warn("텔레그램 전송 실패 summaryId={}: {}", v.id(), e.toString());
            }
        }

        if (!sentIds.isEmpty()) {
            repository.markNotified(sentIds, Instant.now());
        }
        log.info("텔레그램 전송: {}건 성공 (미전송 남으면 다음 주기에)", sentIds.size());
    }

    private String format(ArticleView v) {
        return "📰 [" + v.category().getLabel() + "] " + v.title() + "\n\n"
                + v.summary() + "\n\n🔗 " + v.url();
    }
}
