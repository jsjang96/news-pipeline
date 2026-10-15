# CLAUDE.md — 뉴스 요약·분류 파이프라인

이 파일은 Claude Code가 매 세션 읽는 프로젝트 안내서입니다.
집에 도착하면 프로젝트 루트에 이 파일을 두고 `claude`를 실행하세요.

---

## 프로젝트 목적

IT 뉴스 기사를 주기적으로 크롤링해서, LLM으로 **요약 + 카테고리 분류**한 뒤
웹에서 조회할 수 있게 하는 배치 파이프라인.

**이 프로젝트의 진짜 목표는 "돌아가는 제품"보다 인프라 학습과 포트폴리오다.**
따라서 각 기술 요소는 "왜 썼는지 설명 가능한" 형태로 통합한다:
- Spring Batch: 대량 기사를 chunk 단위로 처리
- LLM(Claude API): 기사 요약·분류 (외부 API 호출)
- Redis: LLM 응답 캐싱(비용 절감) + 조회 캐싱(속도)
- Flyway: DB 스키마 버전 관리
- CI/CD: 직접 작성한 파이프라인으로 배포

---

## 기술 스택

- **언어/프레임워크**: Java 21, Spring Boot 4.1.x (start.spring.io가 3.x를 더 이상 생성하지 않아 현행 4.x 채택)
- **배치**: Spring Batch 6
  - ⚠️ Batch 6부터 JobRepository 기본값이 인메모리(ResourcelessJobRepository)다.
    실행 이력을 DB에 남기려면 `@EnableBatchProcessing @EnableJdbcJobRepository`가 필요하다
    (`BatchInfraConfig` 참고). 배치 메타데이터 테이블은 Flyway V2로 관리한다.
- **DB**: PostgreSQL (개인 프로젝트라 Oracle 대신 가벼운 걸로)
- **ORM**: Spring Data JPA (레거시 MyBatis 대신 학습 겸 JPA 경험)
- **캐시**: Redis
- **마이그레이션**: Flyway
- **LLM**: Anthropic Claude API (모델: Haiku — 요약·분류엔 가장 저렴하고 충분)
- **빌드**: Gradle
- **로컬 인프라**: docker-compose (PostgreSQL + Redis)

---

## 아키텍처 (수집·처리·전송 + 조회 — 각 단계 독립)

**핵심 설계: 각 단계는 서로를 직접 호출하지 않고, DB의 상태 컬럼을 매개로 독립적으로 돈다.**
한 단계가 죽어도 다른 단계는 계속 돈다(실패 격리). 각자 자기 스케줄로 실행.
(예: Claude 장애/키 없음 → 수집은 계속 PENDING 쌓고, 처리만 대기하다 복구되면 이어서 처리.)

현재 스케줄 (application.yml에서 조정):

| 단계 | 주기 | 설정 키 |
|---|---|---|
| 수집 | 10분 | `news.collection.interval-ms` |
| 처리 | 10분 | `news.processing.interval-ms` |
| 전송 | 3분 (한 번에 10건) | `telegram.interval-ms`, `telegram.max-per-run` |

→ 기사 발행부터 텔레그램 도착까지 **최대 약 23분 / 평균 약 12분**.
처리 주기를 줄여도 **Claude 비용은 안 늘어난다**(호출 수는 기사 수로 결정되고, PENDING이 없으면 아무것도 안 함).

연결고리(바통):
- 수집 → 처리 : `raw_article.status` (PENDING → DONE/FAILED)
- 처리 → 전송 : `article_summary.notified_at` (NULL → 전송 시각)

### 1. 수집 (배치, 10분 주기) — `CollectionBatchConfig`
- GeekNews RSS 크롤링 → `raw_article`에 `PENDING` 저장
- URL 중복은 `existsByUrl` + UNIQUE 제약으로 방지
- 크롤링만 수행. LLM 처리와 분리(실패 격리).

### 2. 처리 (배치, 10분 주기) — `ProcessingBatchConfig`
- `raw_article` 중 `PENDING` 건을 chunk로 읽음 (Reader: 커서 방식 — 아래 구현 메모 참고)
- 각 기사를 Claude API(Haiku)로 요약·분류 (Processor, `WebClient` 직접 호출)
  - 처리 전 Redis에서 본문 해시로 중복 확인 → 있으면 API 스킵 (비용 절감, v2)
- 결과를 `article_summary`에 저장, 상태 `DONE`으로 변경 (Writer)
- 실패 시 상태 `FAILED` (v1은 자동 재시도 X — 재처리하려면 PENDING으로 리셋)

### 3. 전송 (배치, 3분 주기) — `DeliveryScheduler`
- `notified_at IS NULL`(아직 안 보낸) 요약을 한 주기에 `max-per-run`(기본 10)건씩 전송
- Telegram Bot API에 `WebClient`로 POST. 보내는 내용: `[카테고리] 제목 / 요약 / 🔗 URL`
- 성공한 건만 `notified_at` 기록 → 중복 전송 방지(멱등성), 실패 건은 다음 주기 재시도
- 토큰/chat_id는 환경변수. 그룹 전송은 chat_id를 그룹 id(음수)로 넣으면 그룹 전원 수신

### 4. 조회 (REST API) — `ArticleController`
- `GET /api/articles` (category 필터·페이지네이션), `GET /api/articles/{id}`
- `article_summary` + `raw_article` 조인(@Query JPQL) → DTO로 반환
- (예정) Look-Aside 캐시: Redis 확인 → 없으면 DB 조회 후 Redis에 캐싱(TTL)

---

## 상태 관리 (멱등성)

각 레코드의 상태 컬럼으로 진행을 추적한다. 배치가 죽었다 살아나도 "어디까지 했나"가
코드가 아니라 DB에 적혀 있어 이어서 처리된다.
- 처리 단계: `raw_article.status` — `PENDING → (PROCESSING) → DONE / FAILED`
  - v1은 PROCESSING을 생략하고 `PENDING → DONE/FAILED` 직행 (단일 인스턴스라 안전).
    다중 인스턴스로 확장 시 읽을 때 PROCESSING 선점(claim)이 필요.
- 전송 단계: `article_summary.notified_at` — NULL(미전송) → 전송 시각(전송됨)

---

## 개발 순서 (레이어를 하나씩 쌓는다)

**한 번에 다 만들지 않는다. 각 단계마다 "돌아가는 것"을 남긴다.**

- **v0 — 뼈대 ✅ 완료**: Spring Boot + PostgreSQL. GeekNews RSS 5분 수집 → raw_article(PENDING).
- **v1 — AI ✅ 완료**: 처리 Job에서 Claude(Haiku) 요약·분류 → article_summary(DONE). 조회 API(`/api/articles`).
- **v1.5 — 전송(텔레그램) ✅ 완료**: 요약된 기사를 텔레그램으로 푸시. `notified_at`로 중복 전송 방지.
- **v2 — 인프라 ← 진행 중**: Redis 캐싱(처리 단계 LLM 본문 중복 → API 스킵, 이후 조회 캐싱), Flyway 정리.
- **v3 — 배포**: 도커라이징 → CI/CD 파이프라인 직접 작성 → 서버 배포.

(알려진 정리거리: Batch 6에서 chunk-oriented 모델이 deprecated — `StepBuilder.chunk(size, txManager)`.
동작엔 문제없고 v7에서 제거 예정이라 추후 새 모델로 이전.)

---

## 코딩 컨벤션 / 주의사항

- **API 키는 절대 코드/YAML에 하드코딩하지 않는다.** 환경변수로 주입. (실무 교훈 그대로)
- 커밋은 각 기능 단위로 작게. 커밋 메시지는 명확하게.
- LLM 호출은 처음엔 SDK 말고 `WebClient`로 직접 REST 호출 (학습 목적).
- `max_tokens`를 코드에서 제한해 출력 비용 폭주 방지.
- 배치 Job은 수집/처리를 **분리**해서 독립 실행 가능하게.

---

## 구현 메모 (설계 패턴 정리)

세션에서 정리한 핵심 개념들. "왜 이렇게 짰는지" 설명할 때 참고.

- **단계 독립 + DB 상태 매개**: 수집/처리/전송은 서로 직접 호출 안 함. `status`, `notified_at`
  같은 상태 컬럼이 바통. → 실패 격리, 독립 주기/재시작/확장. (스케줄러 3개, `spring.task.scheduling.pool.size=3`로 병렬)
- **상태 컬럼 = 진행 메모리**: "어디까지 했나"를 DB에 기록 → 멱등성(중복 처리/전송 방지, 재개 가능).
- **중복 체크 2종 (목적 다름, 둘 다 유지)**:
  - URL 중복(수집): `existsByUrl` + UNIQUE 제약 → 같은 기사 DB 중복 저장 방지(정합성)
  - 본문 해시 중복(처리, v2): Redis → 같은 본문을 또 Claude에 보내는 것 방지(비용). URL이 못 잡는 걸 보완.
- **JPA 조회 2방식**:
  - 쿼리 메서드(이름이 곧 쿼리): `findTop50ByStatusAndIdGreaterThanOrderByIdAsc`, `existsByUrl` — Spring이 이름 파싱해 SQL 자동 생성
  - `@Query`(JPQL 직접): 조인·DTO 변환 등 복잡할 때 (`ArticleSummaryRepository`)
- **배치 Reader는 커서(keyset)로**: "상태로 재조회"는 읽기~커밋 사이 시간차로 중복 처리를 유발 →
  `id > lastId`로 한 번 읽은 건 다시 안 읽게. (상세: `docs/troubleshooting/001-...`)
- **LLM 호출은 WebClient 직접**(`ClaudeClient`): `POST /v1/messages`, 응답의 `CATEGORY:`/`SUMMARY:` 파싱.
  비동기 `Mono`를 배치에선 `.block()`으로 동기 대기. `max_tokens`로 출력 비용 상한.
- **외부 전송도 WebClient**(`TelegramClient`): `POST /bot<token>/sendMessage`.
- **페이지네이션**: `PageRequest.of(page, size)` → SQL `LIMIT size OFFSET page*size`. 전송 백로그 폭탄 방지용.
- **id 생성**: `GENERATED BY DEFAULT AS IDENTITY`(시퀀스). `MAX+1`은 경쟁 조건으로 금지.
  `raw_article_id`는 생성값이 아니라 원본 기사 id를 복사한 FK(참조).
- **환경변수(.env로 관리, 커밋 금지)**: `ANTHROPIC_API_KEY`, `TELEGRAM_BOT_TOKEN`, `TELEGRAM_CHAT_ID`.
  - ⚠️ **Spring Boot는 `.env`를 자동으로 읽지 않는다**(Node/docker-compose 관례임).
    그래서 `application.yml`에 `spring.config.import: optional:file:.env[.properties]` 를 넣어
    `.env`(KEY=VALUE = properties 형식)를 설정으로 읽어들인다. `optional:`이라 파일이 없어도 기동되고,
    운영에선 진짜 환경변수를 쓰면 그쪽이 우선한다.
  - 키가 비면 처리/전송 스케줄러가 **실행 자체를 스킵**한다(401 도배로 기사가 헛되이 FAILED 되는 걸 방지).

상세 트러블슈팅 기록은 `docs/troubleshooting/` 에 번호순으로 쌓는다.

---

## 결정 사항 (확정)

- [x] **뉴스 RSS 소스**: GeekNews 단일로 v0 시작 → `https://news.hada.io/rss/news`
  - (v1 이후 두 번째 소스 후보: ZDNet Korea `https://zdnet.co.kr/news/news_xml.asp`)
- [x] **분류 카테고리**: `AI / 개발 / 클라우드·인프라 / 보안 / 기타`
  - `기타`는 catch-all. 프롬프트에 "위 4개에 안 맞으면 기타"로 명시해 분류 안정화.
- [x] **프로젝트/레포 이름**: `news-pipeline`
