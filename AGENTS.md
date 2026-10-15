# AGENTS.md — 뉴스 요약·분류 파이프라인

이 파일은 Codex가 매 세션 읽는 프로젝트 안내서입니다.
집에 도착하면 프로젝트 루트에 이 파일을 두고 `Codex`를 실행하세요.

---

## 프로젝트 목적

IT 뉴스 기사를 주기적으로 크롤링해서, LLM으로 **요약 + 카테고리 분류**한 뒤
웹에서 조회할 수 있게 하는 배치 파이프라인.

**이 프로젝트의 진짜 목표는 "돌아가는 제품"보다 인프라 학습과 포트폴리오다.**
따라서 각 기술 요소는 "왜 썼는지 설명 가능한" 형태로 통합한다:
- Spring Batch: 대량 기사를 chunk 단위로 처리
- LLM(Codex API): 기사 요약·분류 (외부 API 호출)
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
- **LLM**: Anthropic Codex API (모델: Haiku — 요약·분류엔 가장 저렴하고 충분)
- **빌드**: Gradle
- **로컬 인프라**: docker-compose (PostgreSQL + Redis)

---

## 아키텍처 (3단계)

### 1. 수집 (배치, 5분 주기)
- RSS 소스 크롤링 → `raw_article` 테이블에 저장
- 상태: `PENDING`
- 크롤링만 수행. LLM 처리와 분리(실패 격리).

### 2. 처리 (배치, 10분 주기)
- `raw_article` 중 `PENDING` 건을 chunk로 읽음 (Reader)
- 각 기사를 Codex API로 요약·분류 (Processor)
  - 처리 전 Redis에서 본문 해시로 중복 확인 → 있으면 API 스킵
- 결과를 `article_summary`에 저장, 상태 `DONE`으로 변경 (Writer)
- 실패 시 상태 `FAILED` + 재시도 가능하게

### 3. 조회 (REST API)
- 기사 목록/상세 조회
- Look-Aside 캐시: Redis 확인 → 없으면 DB 조회 후 Redis에 캐싱(TTL)

---

## 상태 관리 (멱등성)

기사마다 상태 컬럼으로 진행 추적. 배치가 죽었다 살아나도 이어서 처리.
`PENDING → PROCESSING → DONE / FAILED`

---

## 개발 순서 (레이어를 하나씩 쌓는다)

**한 번에 다 만들지 않는다. 각 단계마다 "돌아가는 것"을 남긴다.**

- **v0 — 뼈대 ✅ 완료**: Spring Boot + PostgreSQL 연결. GeekNews RSS를 5분 주기로 긁어 raw_article에 PENDING 저장. (로컬 검증 완료: 50건 수집)
- **v1 — AI**: 처리 Job에서 Codex API 요약·분류. 조회 API 1개로 결과 확인. ← **다음 단계**
- **v2 — 인프라**: Redis 캐싱(조회 + LLM 중복), Flyway 정리, 상태 관리 다듬기.
- **v3 — 배포**: 도커라이징 → CI/CD 파이프라인 직접 작성 → 서버 배포.

다음은 **v1**. (v0의 알려진 정리거리: Batch 6에서 chunk-oriented 모델이 deprecated —
`StepBuilder.chunk(size, txManager)`. 동작엔 문제없고 v7에서 제거 예정이라 추후 새 모델로 이전.)

---

## 코딩 컨벤션 / 주의사항

- **API 키는 절대 코드/YAML에 하드코딩하지 않는다.** 환경변수로 주입. (실무 교훈 그대로)
- 커밋은 각 기능 단위로 작게. 커밋 메시지는 명확하게.
- LLM 호출은 처음엔 SDK 말고 `WebClient`로 직접 REST 호출 (학습 목적).
- `max_tokens`를 코드에서 제한해 출력 비용 폭주 방지.
- 배치 Job은 수집/처리를 **분리**해서 독립 실행 가능하게.

---

## 결정 사항 (확정)

- [x] **뉴스 RSS 소스**: GeekNews 단일로 v0 시작 → `https://news.hada.io/rss/news`
  - (v1 이후 두 번째 소스 후보: ZDNet Korea `https://zdnet.co.kr/news/news_xml.asp`)
- [x] **분류 카테고리**: `AI / 개발 / 클라우드·인프라 / 보안 / 기타`
  - `기타`는 catch-all. 프롬프트에 "위 4개에 안 맞으면 기타"로 명시해 분류 안정화.
- [x] **프로젝트/레포 이름**: `news-pipeline`
