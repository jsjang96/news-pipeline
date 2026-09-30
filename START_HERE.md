# 집에 도착하면 — 시작 순서

## 0. 사전 준비 (한 번만)

```bash
# Node/Git 확인 (Git 없으면 설치)
git --version

# Docker Desktop 설치되어 있어야 함 (docker-compose 용)
docker --version

# Claude Code 설치 — 공식 문서에서 최신 방법 확인:
# https://code.claude.com/docs/en/setup
# (Apple Silicon이면 네이티브 arm64 바이너리로 설치)
```

Anthropic 계정 준비:
- **방법 A**: Claude Pro/Max 구독 → Claude Code 로그인에 그대로 사용
- **방법 B**: Anthropic Console에서 API 키 발급 → Claude Code에 등록
  - 이 키는 나중에 **프로젝트 앱이 뉴스 요약할 때 쓰는 키와 별개로 관리**하는 게 깔끔

---

## 1. 프로젝트 폴더 만들고 스타터 파일 넣기

```bash
mkdir news-pipeline && cd news-pipeline
# CLAUDE.md 와 docker-compose.yml 을 이 폴더에 복사해 넣기
git init
```

## 2. 로컬 인프라 띄우기

```bash
docker compose up -d
docker compose ps        # postgres, redis 둘 다 healthy 확인
```

## 3. Claude Code 실행 + 첫 지시

폴더 안에서:
```bash
claude
```

그리고 아래 프롬프트를 순서대로 던진다 👇

---

## 첫 프롬프트 (v0 뼈대 만들기)

> 이 폴더의 CLAUDE.md를 읽어줘. 이 안내서대로 v0를 만들 거야.
> 지금은 v0 범위만: Spring Boot 3.x + Java 21 + Gradle 프로젝트를 세팅하고,
> PostgreSQL(로컬 docker-compose로 이미 떠 있음, newsdb/news/newspass, 5432)에 연결하고,
> raw_article 테이블(id, title, url, content, source, status, created_at)을 Flyway 마이그레이션으로 만들고,
> RSS 피드 하나를 5분마다 크롤링해서 raw_article에 status=PENDING으로 저장하는
> Spring Batch 수집 Job을 만들어줘.
> RSS 소스는 일단 GeekNews(https://news.hada.io/rss/news)로 하자.
> LLM 처리나 조회 API는 아직 만들지 마. v0는 "크롤링→저장"까지만.
> 먼저 프로젝트 구조랑 build.gradle부터 잡고, 하나씩 설명하면서 진행해줘.

---

## v0가 돌면 확인할 것

```bash
# 앱 실행 후, 5분 기다리거나 Job 수동 트리거 후:
docker exec -it news-postgres psql -U news -d newsdb -c "SELECT id, title, status FROM raw_article LIMIT 10;"
```
→ 기사들이 PENDING 상태로 들어와 있으면 v0 성공. 🎉

그 다음 세션에서 v1(LLM 요약·분류)로 넘어간다.

---

## 팁

- Claude Code한테 한 번에 다 시키지 말고 **단계별로** 시키면서 코드를 이해하며 진행.
- 모르는 부분은 "이 코드 왜 이렇게 짰어?" 하고 물어보면 설명해줌. 학습이 목적이니 적극 질문.
- 커밋은 v0 완성 시 한 번: `git add -A && git commit -m "v0: RSS 수집 배치 + raw 저장"`
