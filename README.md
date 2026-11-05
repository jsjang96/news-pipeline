# news-pipeline

IT 뉴스를 수집해 LLM으로 요약·분류하고 텔레그램으로 보내는 개인 프로젝트입니다.
업무에서 다루던 Spring Batch 구조를 직접 설계해보고, 거기에 LLM API 호출 단계를
결합해보는 것을 목적으로 만들었습니다.

## 동작

| 단계 | 주기 | 하는 일 |
|---|---|---|
| 수집 | 30분 | GeekNews RSS 조회 → 신규 기사를 `PENDING` 상태로 적재 |
| 처리 | 30분 | `PENDING` 기사를 Claude API로 요약·분류하고 결과를 Redis에 캐싱 |
| 전송 | 매일 08:00 | 요약이 끝난 기사를 텔레그램으로 발송 |

세 단계는 서로를 직접 호출하지 않고 **기사 상태로만 연결**됩니다.
한 단계가 실패해도 나머지 단계는 계속 돌고, 실패한 기사는 다음 주기에 다시 대상이 됩니다.

## 기술 스택

- Java, Spring Boot, Spring Batch, Spring Data JPA
- PostgreSQL 16 — 스키마는 Flyway로 관리 (배치 메타 테이블 포함)
- Redis 7 — 요약 결과 캐시
- Claude API (`claude-haiku-4-5`), Telegram Bot API
- Gradle, Docker Compose

## 실행

**1. 로컬 인프라 기동**

```bash
docker compose up -d
```

PostgreSQL(5432)과 Redis(6379)가 뜹니다. 끌 때는 `docker compose down`,
데이터까지 지우려면 `docker compose down -v`.

**2. 환경변수 설정**

```bash
cp .env.example .env
```

`.env`에 값을 채웁니다.

| 변수 | 설명 |
|---|---|
| `ANTHROPIC_API_KEY` | 기사 요약·분류에 사용 |
| `TELEGRAM_BOT_TOKEN` | @BotFather에서 발급 |
| `TELEGRAM_CHAT_ID` | @userinfobot에게 말을 걸면 확인 가능 |

**3. 애플리케이션 실행**

```bash
set -a && source .env && set +a && ./gradlew bootRun
```

## 설계 메모

**단계를 상태로 분리했습니다.**
수집·요약·전송을 하나의 흐름으로 묶지 않고 기사 상태로 나눴습니다.
LLM 호출이나 텔레그램 전송이 실패해도 수집은 계속되고, 실패분만 다음 주기에 다시 처리됩니다.

**스케줄러 스레드 풀을 분리했습니다.**
`task.scheduling.pool.size: 3`으로 두어 세 스케줄러가 서로 블로킹되지 않게 했습니다.

**호출 횟수를 줄이려고 주기를 다시 잡았습니다.**
처음에는 수집·처리를 10분, 전송을 3분 주기로 돌렸습니다. 뉴스가 실시간으로 반응해야 하는
성격이 아닌데 스케줄러가 하루에 수백 번씩 깨어나고 있어서, 수집·처리는 30분으로 늘리고
전송은 아침 8시 한 번으로 바꿨습니다. 하루 기준 수집·처리는 144회에서 48회로,
전송은 480회에서 1회로 줄었습니다.

**같은 본문을 다시 요약하지 않습니다.**
요약 결과를 Redis에 캐싱(TTL 7일)해 중복 호출을 막습니다.
LLM 호출이 비용이 드는 구간이라 재시도와 중복을 줄이는 쪽으로 잡았습니다.

**출력 길이에 상한을 뒀습니다.**
`max-tokens: 300`으로 응답을 제한해 건당 비용이 예측 가능하도록 했습니다.

**한 번에 쏟아지지 않게 했습니다.**
밀린 기사가 한꺼번에 나가지 않도록 전송 건수에 상한을 두었습니다.

**기동 시 자동 실행을 껐습니다.**
`batch.job.enabled: false`로 애플리케이션이 뜰 때 Job이 자동 실행되지 않게 하고,
스케줄러를 통해서만 실행되도록 했습니다.

**비밀값은 코드에 두지 않습니다.**
API 키와 토큰은 환경변수로만 주입하며, `.env`는 커밋에서 제외하고 `.env.example` 템플릿만 추적합니다.

## 디렉터리

```
src/main/java/com/jinsu/newspipeline/   애플리케이션 코드
src/main/resources/
  application.yml                       설정
  db/migration/                         Flyway 마이그레이션
docs/troubleshooting/                   개발 중 겪은 문제와 해결 과정
docker-compose.yml                      로컬 PostgreSQL + Redis
.env.example                            환경변수 템플릿
```

## 비고

설계와 구현에 Claude, Cursor 등 AI 코딩 도구를 활용했습니다. 커밋 이력에 공동 작성자로 남아 있습니다.
스케줄 주기, 캐싱 범위, 비용 상한 같은 선택은 직접 판단해 조정하고 그 과정을 커밋 메시지에 남겼습니다.
