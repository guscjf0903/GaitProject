# 미구현 기능 현황 (Missing Features)

작성일: 2026-03-01
기준 문서: `docs/planning/project_plan.md`

이 문서는 기획서에 명시된 요구사항 중, **현재(2026년 3월 기준) 코드베이스에 아직 구현되지 않았거나 스텁(Stub) 상태인 항목들**을 정리한 것입니다.

## 1. 지능형 RAG 및 Vector 파이프라인
- **상태:** DB에 `pgvector` 익스텐션과 엔티티(`embedding` 컬럼)는 존재하나, 로직 미구현. `RagInterceptor`는 단순 스텁(가짜 텍스트 주입) 상태.
- **미구현 상세:**
  - **문서 임베딩:** 커밋 생성 시 또는 메시지 저장 시 텍스트를 Vector로 변환(OpenAI Embedding API 등 활용)하여 DB에 저장하는 파이프라인 부재.
  - **시맨틱 라우터 (3-Tier Routing):** 사용자의 입력 의도(단순 대화 vs 이전 검색)를 0.05초 내로 분석하여 RAG를 태울지 말지 결정하는 로직 미구현 (기획서 4.4 참조).
  - **하이브리드 검색:** pgvector를 활용한 코사인 유사도 검색 API 및 쿼리 추출 로직 미구현.

## 2. 사용자 토큰 과금(Billing) 및 플랜 제한 시스템
- **상태:** 토큰 사용량(`totalTokens`)을 DB에 저장하는 것까지는 완료되었으나, 이를 실제 사용자의 '잔여 크레딧'에서 차감하거나 플랜 한도를 제어하는 로직이 없음.
- **미구현 상세:**
  - **프리미엄 지갑 / Cheap Pool 시스템:** 기획서 상의 월 예산(지갑) 차감 로직 부재 (`TokenBillingService` 미구현).
  - **플랜별 모델 제한:** 무료 사용자가 프리미엄 모델을 선택하지 못하도록 막는 서버 검증 로직 및 지갑 잔액 0원 시 자동 Fallback 로직 미구현.
  - **RAG 크레딧:** RAG 검색 1회당 크레딧 1회 차감 및 알림 전송 로직 미구현.

## 3. 지능형 머지 (Conflict Resolution UI)
- **상태:** 백엔드 `MergeService`는 Fast-forward 수준만 임시 구현되어 있으며 AI 기반 머지가 없음.
- **미구현 상세:**
  - `Master` 플랜의 핵심 기능인 AI Deep Merge (서로 다른 내용을 통합하는 AI 요약) 부재.
  - "합치기 애매한 부분(Conflict)은 리포트하여 사용자에게 선택지를 주는 방식" (Conflict Resolution UI) 프론트엔드/백엔드 미구현.

## 4. UI/UX 디테일
- **상태:** 코어 기능 위주로 구성되어 있어 기획서의 세부 UX가 누락됨.
- **미구현 상세:**
  - **1.2M Tokens left 연동:** `ChatPage.vue` 좌측 하단에 하드코딩된 "1.2M Tokens left"와 "Standard Plan"을 실제 백엔드 사용자 데이터(`UsersService`)와 연동 필요.
  - **SSE RAG 알림:** RAG 검색 시 사용자에게 "과거 기록 검색 중... (-1 Credit)" 이벤트를 실시간으로 띄워주는 UI 효과 부재.

## 5. 다양한 AI Provider 연동
- **상태:** Spring AI를 통한 OpenAI(GPT-4o-mini)는 완벽히 연동되었으나 Gemini는 스텁 상태.
- **미구현 상세:**
  - `GeminiFlashService`, `GeminiProService` 클래스가 존재하나 실제 API 호출 코드가 작성되지 않은 TODO 상태. (Spring AI Vertex AI 또는 Google AI 모듈 추가 필요).