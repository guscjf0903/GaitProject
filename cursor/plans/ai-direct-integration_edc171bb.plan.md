---
name: ai-direct-integration
overview: 현재 구현된 프론트/백엔드 진행 현황을 기획서와 대조해 정리하고, 1차 목표(채팅 스트리밍 + 커밋 요약 생성)를 OpenAI 기반으로 실제 연동하는 단계별 구현 계획을 제시합니다.
todos:
  - id: status-summary-doc
    content: 프론트/백엔드 진행 현황을 기획서 대비로 문서화(현재 메시지의 정리 내용을 기준으로 확정)
    status: completed
  - id: enable-spring-ai-openai
    content: "`build.gradle.kts`에 Spring AI OpenAI 의존성/BOM 추가 및 설정 키 정리"
    status: completed
  - id: implement-openai-streaming
    content: OpenAI 기반 `AiService` 구현(스트리밍 chunk → 콜백) 및 `AiRouter` 연결
    status: completed
  - id: chatstream-error-events
    content: "`ChatStreamController`에 에러 이벤트(ANSWER_ERROR 등) 및 종료 처리 개선"
    status: completed
  - id: commit-summary-ai
    content: 커밋 생성 시 AI 요약(JSON) 자동 생성 서비스 구현 및 `CommitService`/`MessageService`에 연결
    status: completed
  - id: frontend-commit-integration
    content: 프론트 `ChatPage.vue` 커밋 모달을 실제 `/commits` API 호출로 연결하고 그래프 동기화
    status: completed
isProject: false
---

# GaitProject 진행현황 정리 + OpenAI 직접 연동 계획

## 현재 어디까지 됐나 (기획서 대비)

### 프론트엔드 (구현됨)

- **로그인/회원가입 화면**: 이메일 기반 로그인 → 토큰 로컬 저장 후 이동
- **워크스페이스 화면**: 워크스페이스 목록/생성, 워크스페이스의 브랜치 목록 조회, 브랜치 선택 후 채팅 화면 진입
- **채팅 화면**:
  - 채팅 UI, 타임라인 조회(메시지 목록), 메시지 저장
  - **SSE 스트리밍 수신** (`/api/chat/stream`)으로 `ANSWER_CHUNK`/`ANSWER_DONE` 이벤트 처리
  - 커밋/브랜치 생성/체크아웃 UI는 있으나 **백엔드 연동이 일부 미완(로컬 상태만 업데이트)**

### 백엔드 (구현됨)

- **Auth**: `/api/auth/signup`, `/api/auth/login` (JWT 발급) — MVP는 email 기반
  - 파일: [/Users/hyunchuljung/Desktop/GaitProject/src/main/kotlin/com/gait/gaitproject/controller/auth/AuthController.kt](/Users/hyunchuljung/Desktop/GaitProject/src/main/kotlin/com/gait/gaitproject/controller/auth/AuthController.kt)
- **Workspace/Branch/Message/Commit/Merge 기본 CRUD 흐름**
  - Workspace 생성 시 `main` 브랜치 자동 생성
  - 메시지 저장 + 타임라인 조회
  - 커밋 생성 시 브랜치 head 갱신 + 미커밋 메시지 attach
  - Merge는 **FAST_FORWARD 최소 구현**(toBranch head 이동)
- **SSE 스트리밍 엔드포인트 골격**: `/api/chat/stream`는 `SseEmitter`로 chunk 이벤트 전송
  - 파일: [/Users/hyunchuljung/Desktop/GaitProject/src/main/kotlin/com/gait/gaitproject/controller/chat/ChatStreamController.kt](/Users/hyunchuljung/Desktop/GaitProject/src/main/kotlin/com/gait/gaitproject/controller/chat/ChatStreamController.kt)

### 백엔드 (미완/스텁)

- **실제 LLM 호출**: 현재는 기본값이 `app.ai.use-stub=true`라서 `StubAiService`로 “가짜 스트리밍” 동작
  - 파일: [/Users/hyunchuljung/Desktop/GaitProject/src/main/kotlin/com/gait/gaitproject/service/ai/StubAiService.kt](/Users/hyunchuljung/Desktop/GaitProject/src/main/kotlin/com/gait/gaitproject/service/ai/StubAiService.kt)
- **Gemini/OpenAI Spring AI 연동**: `build.gradle.kts`에 Spring AI 의존성이 주석 처리된 상태
  - 파일: [/Users/hyunchuljung/Desktop/GaitProject/build.gradle.kts](/Users/hyunchuljung/Desktop/GaitProject/build.gradle.kts)
- **커밋 요약 생성(AI)**: `CommitService.create()`는 요청으로 받은 요약을 저장만 함. 자동커밋도 현재는 하드코딩 텍스트.
  - 파일: [/Users/hyunchuljung/Desktop/GaitProject/src/main/kotlin/com/gait/gaitproject/service/workspace/CommitService.kt](/Users/hyunchuljung/Desktop/GaitProject/src/main/kotlin/com/gait/gaitproject/service/workspace/CommitService.kt)
  - 파일: [/Users/hyunchuljung/Desktop/GaitProject/src/main/kotlin/com/gait/gaitproject/service/chat/MessageService.kt](/Users/hyunchuljung/Desktop/GaitProject/src/main/kotlin/com/gait/gaitproject/service/chat/MessageService.kt)
- **RAG/pgvector**: `RagInterceptor`는 휴리스틱 + 로그 저장 + “stub injectedText”만 주입(실검색 없음)
  - 파일: [/Users/hyunchuljung/Desktop/GaitProject/src/main/kotlin/com/gait/gaitproject/service/rag/RagInterceptor.kt](/Users/hyunchuljung/Desktop/GaitProject/src/main/kotlin/com/gait/gaitproject/service/rag/RagInterceptor.kt)

## 1차 목표

- **채팅 SSE 스트리밍을 “진짜 OpenAI”로 교체**
- **커밋 생성 시 요약을 AI가 생성하도록 구현**(수동 커밋 + 자동 커밋 모두)

## 구현 전략(권장)

### A. OpenAI 연동 방식

- **Spring AI OpenAI Starter 사용**을 1순위로 권장
  - 이유: 스트리밍/모델 옵션/메시지 구조를 프레임워크가 표준화, 설정은 `application.yml`로 집중
- 대안: 현재 포함된 `WebClient`(webflux)로 OpenAI HTTP 직접 호출(추후 필요 시)

### B. 스트리밍 형태

- **현재 MVC + `SseEmitter` 구조 유지**
  - 변경 최소화(프론트도 이미 SSE 이벤트 수신 구현)
  - 나중에 WebFlux로 전환은 별도 리팩터링 과제로 분리

### C. 커밋 요약 생성

- `CommitService.create()`에 **“요약 자동 생성” 옵션**을 추가
  - 입력(미커밋 메시지들 + 직전 커밋 longSummary 일부)
  - 출력(JSON): `keyPoint`, `shortSummary`, `longSummary`
- 모델은 비용을 고려해 **요약 전용 모델(예: gpt-4o-mini)** 를 기본으로, 채팅은 gpt-4o(또는 동일) 사용

## 단계별 작업(상세)

### Phase 1) Spring AI OpenAI 연결 및 채팅 스트리밍 교체

- `build.gradle.kts`
  - Spring AI BOM/Starter 의존성 추가(주석 해제 + 버전 최신화)
- `application.yml`
  - `spring.ai.openai.api-key`, `spring.ai.openai.chat.options.model` 등 활성화
  - `app.ai.use-stub=false`로 기본 전환(로컬은 env로 제어)
- `service/ai/`
  - `OpenAiChatService`(신규) 또는 기존 `Gemini*Service` 대신 **OpenAI용 구현체** 추가
  - `AiRouter`에서 `PlanType`별로 OpenAI 서비스 선택하도록 갱신
- `ChatStreamController`
  - OpenAI 스트리밍 chunk를 `ANSWER_CHUNK`로 변환
  - 실패 시 `ANSWER_ERROR` 같은 이벤트를 추가로 보내고 종료하도록 개선(프론트 UX 안정)

### Phase 2) 커밋 요약 자동 생성(AI)

- `service/workspace/CommitService`
  - 요청에서 요약이 비었거나 `autoGenerateSummary=true`이면, 미커밋 메시지들을 읽어 요약 생성 후 저장
- `service/chat/MessageService`
  - 자동 커밋 트리거 시 하드코딩 요약 대신 **요약 생성 서비스 호출**
- 신규 서비스 제안
  - `service/ai/CommitSummaryService` (또는 `service/workspace/CommitSummaryService`)
  - 프롬프트/JSON 파싱/재시도/길이 제한(토큰 예산)을 한 곳에 집중

### Phase 3) 프론트 연동 정리(1차 목표 범위 내)

- `GaitProject_frontend/src/pages/ChatPage.vue`
  - 커밋 모달에서 실제 `POST /commits` 호출 후, 응답(`CommitCreateResultResponse`)으로 그래프/헤드 동기화
- `GaitProject_frontend/src/pages/WorkspacePage.vue`
  - 브랜치 생성 모달이 있다면 실제 `POST /branches` 호출로 동기화

## 테스트/검증 체크리스트

- 로컬에서 `OPENAI_API_KEY` 주입 후:
  - 채팅 SSE가 끊기지 않고 chunk가 순서대로 수신되는지
  - 커밋 생성 시 요약 3종이 채워지는지
  - 자동 커밋(20개) 트리거에서 요약 생성이 동작하는지

## 다음 단계(로드맵, 이번 1차 목표 이후)

- RAG: pgvector 임베딩 생성/저장/검색 파이프라인(현재 `RagInterceptor` stub 교체)
- 크레딧/토큰 과금: Hold→Commit/Rollback 트랜잭션
- 워크스페이스/브랜치 접근권한 검증(소유권 체크)

