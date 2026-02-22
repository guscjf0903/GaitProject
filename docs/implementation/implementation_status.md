## 구현 현황 (기획서 `docs/project_plan.md` 기준)

작성일: 2026-02-07

이 문서는 현재 코드 기준으로 **어디까지 구현됐는지**를 빠르게 파악하기 위한 체크리스트입니다.

### 프론트엔드 (`GaitProject_frontend`)

- **로그인/회원가입**
  - 이메일 기반 로그인/회원가입 플로우 구현
  - JWT(`gait_access_token`) + userId(`gait_user_id`)를 `localStorage`에 저장
  - 라우트 가드로 인증 필요 페이지 보호
- **워크스페이스**
  - 워크스페이스 목록/생성 UI 구현 및 API 연동
  - 워크스페이스의 브랜치 목록 조회 UI 및 API 연동
- **채팅**
  - 메시지 타임라인 조회 및 메시지 저장 API 연동
  - **SSE 스트리밍 수신**(`POST /api/chat/stream`) 구현: `ANSWER_CHUNK` → `ANSWER_DONE`
  - 커밋/브랜치 생성/체크아웃 UI는 존재하나, 일부는 로컬 상태 업데이트 중심(백엔드 연동 보강 필요)

### 백엔드 (`GaitProject`)

#### 구현된 REST API (DB 연동 포함)

- **Auth**
  - `POST /api/auth/signup`
  - `POST /api/auth/login`
- **Workspaces**
  - `POST /api/workspaces` (생성 시 기본 브랜치 `main` 자동 생성)
  - `GET /api/workspaces/{workspaceId}`
  - `GET /api/users/{userId}/workspaces`
- **Branches**
  - `POST /api/workspaces/{workspaceId}/branches`
  - `GET /api/workspaces/{workspaceId}/branches`
- **Messages**
  - `POST /api/workspaces/{workspaceId}/branches/{branchId}/messages`
  - `GET /api/workspaces/{workspaceId}/branches/{branchId}/messages/timeline`
- **Commits**
  - `POST /api/workspaces/{workspaceId}/branches/{branchId}/commits`
  - 커밋 생성 시 브랜치 head 갱신 및 미커밋 메시지들을 커밋에 귀속
- **Merges**
  - `POST /api/workspaces/{workspaceId}/merges`
  - `mergeType=FAST_FORWARD`인 경우 toBranch head를 fromBranch head로 이동(최소 구현)

#### SSE 스트리밍(골격 구현)

- `POST /api/chat/stream`
  - `SseEmitter`로 `ANSWER_CHUNK`(여러 번) → `ANSWER_DONE`(1번) 전송
  - 컨텍스트 조립: `ContextBuilder`에서 토큰 예산 기반으로 System/Recent/Head/Lineage를 합쳐 prompt 생성
  - RAG 인터셉트: `RagInterceptor`는 현재 휴리스틱으로만 “stub retrieval text”를 주입

#### 아직 “실제 구현”이 아닌 부분(스텁/미완)

- **실제 LLM 호출**
  - 기본 설정은 `app.ai.use-stub=true`로 `StubAiService`가 “가짜 스트리밍”을 반환
  - `GeminiFlashService`, `GeminiProService`는 현재 TODO 수준(실제 API 호출 미구현)
- **커밋 요약 자동 생성(AI)**
  - 커밋 생성은 가능하지만 요약(`keyPoint`, `shortSummary`, `longSummary`)을 실제 AI로 생성하지 않음
  - 자동 커밋 트리거도 현재는 하드코딩 요약을 저장
- **RAG/pgvector**
  - 실제 임베딩 생성/저장/검색 파이프라인은 미구현
  - 현재는 “검색했다는 힌트”만 prompt에 주입

