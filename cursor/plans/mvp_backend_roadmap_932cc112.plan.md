---
name: MVP Backend Roadmap
overview: 현재 구현된 Workspace/Branch/Message/Commit/Merge CRUD 기반 위에, WebFlux SSE 채팅 스트리밍과 JWT 인증을 추가하고, 기획서의 핵심인 Dynamic Context + RAG + 크레딧/플랜 정책을 순차적으로 구현합니다.
todos:
  - id: webflux-sse
    content: spring-webflux 전환 및 /api/chat/stream SSE 컨트롤러/이벤트 DTO 뼈대 구축
    status: completed
  - id: jwt-auth
    content: Spring Security + JWT 인증 도입 및 기존 API의 userId 전달 제거 방향 정리
    status: completed
    dependencies:
      - webflux-sse
  - id: context-builder
    content: Dynamic Context Builder(Recent/Head/Lineage) 구현 + 필요한 조회 쿼리 보강
    status: completed
    dependencies:
      - jwt-auth
  - id: ai-router
    content: AiService 추상화 + 모델 라우팅 + 스트리밍 응답 연결
    status: completed
    dependencies:
      - context-builder
  - id: rag-interceptor
    content: RAG Interceptor(크레딧/로그/pgvector 검색) 구현 및 SSE RAG_USAGE 이벤트 전송
    status: completed
    dependencies:
      - ai-router
  - id: auto-commit-merge
    content: 자동 커밋/요약/머지 정책(FF/Squash/Deep) 단계적 구현
    status: completed
    dependencies:
      - rag-interceptor
---

# 백엔드 구현 로드맵 (현재→MVP)

## 현재 구현 완료 범위

- **도메인/스키마 용어 정리**: `Repository` 개념을 **`Workspace`**로 통일, 문서 스키마도 `workspaces/workspace_id`로 정리
- 참고: [docs/db_schema.md](docs/db_schema.md), [docs/project_plan.md](docs/project_plan.md)
- **기본 REST API + 레이어링**: DTO/Service/Controller 분리, `ApiResponse(code,message,data)` + `GlobalExceptionHandler` 적용
- 참고: [src/main/kotlin/com/gait/gaitproject/dto/common/ApiResponse.kt](src/main/kotlin/com/gait/gaitproject/dto/common/ApiResponse.kt), [src/main/kotlin/com/gait/gaitproject/exception/GlobalExceptionHandler.kt](src/main/kotlin/com/gait/gaitproject/exception/GlobalExceptionHandler.kt)
- **핵심 엔티티 CRUD(최소)**
- Workspace 생성/조회/유저별 목록
- Branch 생성(기본 브랜치 처리)/목록
- Message 저장(브랜치별 `sequence` 증가)/타임라인 조회
- Commit 생성 + 브랜치 head 갱신 + 미커밋 메시지 commit 귀속
- Merge 생성(+ FAST_FORWARD 최소 동작)

## 앞으로의 구현 순서(추천)

## 1단계: WebFlux 전환 및 SSE 스트리밍 API 뼈대

- **목표**: 기획서의 `/api/chat/stream` (SSE) 기반으로 “스트리밍 응답” 파이프라인을 먼저 고정
- **작업**
- `spring-boot-starter-webflux` 도입 및 런타임을 WebFlux로 정리(중복 MVC 의존성/설정 충돌 방지)
- SSE 응답 포맷을 `ApiResponse`와 결합해 **항상 동일 JSON 구조**로 이벤트 전송
- SSE 이벤트 타입(예: `ANSWER_CHUNK`, `ANSWER_DONE`, `RAG_USAGE`, `ERROR`)를 DTO로 정의
- **핵심 파일(예상)**
- [build.gradle.kts](build.gradle.kts)
- `controller/chat/ChatStreamController.kt`(신규)
- `dto/chat/SseEventDtos.kt`(신규)

## 2단계: JWT 인증/인가(최소) + userId 전달 방식 정리

- **목표**: 프론트에서 매번 `userId`를 넘기지 않도록, 토큰 기반으로 유저 식별
- **작업**
- Spring Security + JWT 필터 구성
- 최소 엔드포인트: 회원가입/로그인/토큰재발급(선택)
- 기존 API에서 `userId`를 Path/RequestBody로 받던 부분을 점진적으로 제거하고, `AuthenticationPrincipal`/SecurityContext에서 유저 ID 추출
- **핵심 파일(예상)**
- `security/*` (신규)
- `controller/auth/*` (신규)
- [src/main/kotlin/com/gait/gaitproject/domain/user/entity/User.kt](src/main/kotlin/com/gait/gaitproject/domain/user/entity/User.kt)

## 3단계: Dynamic Context Builder 구현(기획서 2장)

- **목표**: “System + Recent + Head + Lineage” 토큰 예산 기반 조립 로직을 서비스로 구현
- **작업**
- Recent History: 미커밋 메시지 N개 조회
- Head Context: 브랜치 head 커밋의 long_summary
- Lineage: parent 체인을 따라 short_summary/keyword 수준으로 backfill
- 토큰 예산은 우선 “문자수 기반 근사”로 시작하고, 모델 토크나이저가 확정되면 교체
- **핵심 파일(예상)**
- `service/chat/ContextBuilder.kt`(신규)
- Repository 쿼리 확장: 커밋/메시지 조회 최적화

## 4단계: AI 서비스 추상화 + 라우팅(기획서 4.1)

- **목표**: `AiService` 인터페이스와 구현체(Flash/Pro 등) 및 라우터 도입
- **작업**
- `AiService.generateResponse(prompt): Flux<...>` 형태로 스트리밍 응답
- `summarizeCommit`, `mergeSummaries` API도 인터페이스에 포함
- 플랜별 모델 선택(Free/Standard/Master) 최소 로직부터 적용
- **핵심 파일(예상)**
- `service/ai/AiService.kt`(신규)
- `service/ai/*Service.kt`(신규)
- `service/ai/AiRouter.kt`(신규)

## 5단계: RAG Interceptor + pgvector 검색 + 크레딧/로그(기획서 4.2)

- **목표**: 모델이 “검색 필요”를 판단하면, 크레딧/한도를 체크하고 pgvector로 검색 후 재호출
- **작업**
- embeddings 저장 전략 결정: (a) commit만, (b) message+commit
- `CreditLog` 기록 + `users.rag_calls_today` 증감 + 일일 한도 적용
- SSE로 `RAG_USAGE` 이벤트 전송
- **핵심 파일(예상)**
- `service/rag/*` (신규)
- `repository` 계층: 벡터 검색용 QueryDSL/Native SQL(필요 시)

## 6단계: 커밋 요약 자동화 + 머지 고도화(기획서 4.3/4.5)

- **목표**: “20턴 누적 자동 커밋” + 플랜별 머지(FF/Squash/Deep)
- **작업**
- 자동 커밋 트리거(메시지 카운트/시간 기반)
- 요약 생성 후 `commits.short_summary/long_summary` 저장 + message commit_id 업데이트
- Deep Merge는 Master 플랜만(충돌 보고 포함)

## 7단계: 운영성(필수 체크)

- **목표**: 프론트가 안정적으로 다루는 API
- **작업**
- 표준 에러 코드 정리(`VALIDATION_ERROR`, `NOT_FOUND`, `UNAUTHORIZED`, `FORBIDDEN`, `INTERNAL_ERROR` 등)
- 로그/트레이싱, API 문서화(OpenAPI) 도입