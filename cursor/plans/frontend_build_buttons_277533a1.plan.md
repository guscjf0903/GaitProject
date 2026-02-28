---
name: Frontend Build Buttons
overview: "Cursor Plans UI에서 단계별 Build 버튼으로 프론트(MVP: 로그인→워크스페이스/브랜치→타임라인→POST SSE 채팅)를 이어서 구현할 수 있도록, 현재 코드 상태를 기준으로 실행 가능한 Todo 단계로 정리합니다."
todos:
  - id: env-and-openapi
    content: "프론트 실행 환경 점검: VITE_API_BASE_URL 정리, 필요 시 openapi 재생성(generate:api) 및 generated 서비스/모델 사용 방식 확인"
    status: pending
  - id: auth-guard-and-navigation
    content: "인증 가드/리다이렉트 정리: 미인증 시 /login 이동, 로그인 성공 시 /로 이동, 토큰/유저ID 보관 정책 점검"
    status: pending
    dependencies:
      - env-and-openapi
  - id: workspace-branch-flow
    content: "WorkspacePage 실제 플로우 안정화: 워크스페이스 목록/생성, 선택 시 브랜치 목록 로딩, main 브랜치 생성 호출을 실제 스펙에 맞게 수정"
    status: pending
    dependencies:
      - auth-guard-and-navigation
  - id: timeline-load
    content: ChatPage 타임라인 로딩을 실제 API 응답에 맞춰 반영(메시지 role 매핑/정렬/after, limit) + 기존 데모 스냅샷 로직과 충돌 정리
    status: pending
    dependencies:
      - workspace-branch-flow
  - id: post-sse-chat
    content: POST /api/chat/stream 스트리밍을 실제 SSE payload(ApiResponse/SseEvent)에 맞춰 누적 렌더링하고, 에러/중단(Abort) 처리까지 마무리
    status: pending
    dependencies:
      - timeline-load
  - id: smoke-run
    content: "로컬에서 end-to-end 스모크 확인: 로그인→워크스페이스 생성→브랜치 생성→채팅 전송→스트리밍 수신→타임라인 재조회"
    status: pending
    dependencies:
      - post-sse-chat
isProject: false
---

# GaitProject_frontend Build Plan (Cursor Plans)

## 목표

- `GaitProject_frontend`에서 **로그인 → 워크스페이스/브랜치 선택 → 타임라인 로딩 → POST SSE 스트리밍 채팅**까지 end-to-end로 안정 동작
- Cursor를 껐다 켜도 이 Plan은 **Cursor Plans UI에 남아서** 각 단계별 Build 버튼으로 재개

## 기준 문서/파일

- Plan 원문: `[/Users/hyunchuljung/Desktop/GaitProject_frontend/docs/frontend_plan.md](/Users/hyunchuljung/Desktop/GaitProject_frontend/docs/frontend_plan.md)`
- 라우팅: `[/Users/hyunchuljung/Desktop/GaitProject_frontend/src/router/index.ts](/Users/hyunchuljung/Desktop/GaitProject_frontend/src/router/index.ts)`
- 인증 스토어: `[/Users/hyunchuljung/Desktop/GaitProject_frontend/src/stores/auth.ts](/Users/hyunchuljung/Desktop/GaitProject_frontend/src/stores/auth.ts)`
- 페이지: `[/Users/hyunchuljung/Desktop/GaitProject_frontend/src/pages/LoginPage.vue](/Users/hyunchuljung/Desktop/GaitProject_frontend/src/pages/LoginPage.vue)`, `[/Users/hyunchuljung/Desktop/GaitProject_frontend/src/pages/WorkspacePage.vue](/Users/hyunchuljung/Desktop/GaitProject_frontend/src/pages/WorkspacePage.vue)`, `[/Users/hyunchuljung/Desktop/GaitProject_frontend/src/pages/ChatPage.vue](/Users/hyunchuljung/Desktop/GaitProject_frontend/src/pages/ChatPage.vue)`
- POST SSE 클라이언트: `[/Users/hyunchuljung/Desktop/GaitProject_frontend/src/api/sseChatStream.ts](/Users/hyunchuljung/Desktop/GaitProject_frontend/src/api/sseChatStream.ts)`
- OpenAPI 설정: `[/Users/hyunchuljung/Desktop/GaitProject_frontend/src/api/config.ts](/Users/hyunchuljung/Desktop/GaitProject_frontend/src/api/config.ts)`

## 전제(로컬 실행)

- 백엔드가 `http://localhost:8080`에서 실행 중
- 프론트는 `npm install` 후 `npm run dev`
- OpenAPI 갱신이 필요하면 `npm run generate:api`

## 구현 범위(이번 Plan이 다루는 것)

- 프론트 코드 기준으로 **MVP 플로우**를 실제 API 응답 구조에 맞춰 고정
- `ChatPage.vue`의 데모(가짜 commits/messages)와 실제 API를 충돌 없이 정리

