package com.jinsu.newspipeline.process;

import com.jinsu.newspipeline.domain.Category;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 요약·분류 결과를 Redis에 캐싱한다(비용 절감).
 * 키 = "summary:" + 본문해시(SHA-256). 같은 본문이면 Claude를 다시 부르지 않고 캐시 재사용.
 *
 * URL 중복(existsByUrl)이 못 잡는 "URL은 다른데 내용이 같은" 경우를 여기서 걸러 API 호출을 아낀다.
 * 값은 "카테고리이름\t요약" 형태의 단순 문자열(Jackson 버전 이슈 피하려고 직접 인코딩).
 */
@Component
public class SummaryCache {

    private static final String KEY_PREFIX = "summary:";
    private static final char DELIM = '\t';

    private final StringRedisTemplate redis;
    private final Duration ttl;

    public SummaryCache(StringRedisTemplate redis,
                        @Value("${news.cache.summary-ttl-hours:168}") long ttlHours) {
        this.redis = redis;
        this.ttl = Duration.ofHours(ttlHours);
    }

    /** 캐시에 있으면 Classification 반환, 없으면 empty. */
    public Optional<Classification> get(String title, String content) {
        String value = redis.opsForValue().get(key(title, content));
        if (value == null) {
            return Optional.empty();
        }
        int i = value.indexOf(DELIM);
        if (i < 0) {
            return Optional.empty(); // 깨진 값은 miss 취급
        }
        Category category = Category.fromLabel(value.substring(0, i));
        String summary = value.substring(i + 1);
        return Optional.of(new Classification(category, summary));
    }

    /** 요약 결과를 TTL과 함께 저장. */
    public void put(String title, String content, Classification c) {
        String value = c.category().name() + DELIM + c.summary();
        redis.opsForValue().set(key(title, content), value, ttl);
    }

    private String key(String title, String content) {
        String base = nullToEmpty(title) + "\u0000" + nullToEmpty(content);
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(base.getBytes(StandardCharsets.UTF_8));
            return KEY_PREFIX + HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256은 모든 JVM에 있어서 실제론 발생하지 않음
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
