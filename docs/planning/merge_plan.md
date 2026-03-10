# 머지(Merge) 기능 구현 계획서

작성일: 2026-03-01
관련 기획서: `docs/planning/project_plan.md` (4.5 머지 프로세스)

## 1. 목적 및 현황
- **목표:** 분기된 대화(브랜치)의 내용을 병합하여 지식을 통합하는 기능 구현. 사용자 플랜에 따라 병합의 수준(단순 결합 vs AI 지능형 결합)을 다르게 제공.
- **현재 구현 상태:** `MergeService.create`에서 `FAST_FORWARD` (단순히 head 커밋 포인터만 이동하는 방식) 기능만 최소 구현되어 있으며, 플랜별 권한 체크나 AI 요약 기반 머지 로직은 전무한 상태.

## 2. 플랜별 머지 정책 및 로직

### 2.1 Free 플랜
- **정책:** 머지 기능 사용 불가.
- **구현:** `MergeService`에서 사용자의 플랜이 `FREE`일 경우 `UnauthorizedException` 또는 `Forbidden` 에러 반환.

### 2.2 Standard 플랜 (Squash Merge)
- **정책:** 단순 결합 방식. 깊은 충돌 처리는 불가능하나, 분기된 내용들을 하나로 묶어 가져옴.
- **구현 로직:**
  1. `fromBranch`와 `toBranch`의 공통 조상(Common Ancestor) 커밋을 찾음.
  2. `fromBranch`의 헤드부터 공통 조상 직전까지의 커밋들을 역순으로 수집.
  3. 수집된 커밋들의 `short_summary`를 단순히 시간 순서대로 이어 붙여 텍스트 조합.
  4. `toBranch`에 새로운 `Commit`을 생성하되, `keyPoint`는 "Merged from {fromBranch.name}"로 설정하고 이어붙인 텍스트를 `longSummary`로 저장.
  5. 해당 커밋의 `isMerge` 플래그를 `true`로, `mergeParentId`를 `fromBranch`의 헤드로 설정.

### 2.3 Master / Pro 플랜 (지능형 Deep Merge)
- **정책:** 고성능 AI 모델(Gemini Pro / GPT-4o)을 활용하여 두 브랜치의 내용을 지능적으로 통합. 논리적 충돌을 해소하고 중복을 제거.
- **구현 로직:**
  1. `MergeSummaryAiService` 신규 클래스 생성.
  2. 양쪽 브랜치(`from`, `to`)의 최근 커밋 요약(long_summary 등)을 컨텍스트로 수집.
  3. AI에게 "두 브랜치 내용 통합, 중복 제거, 논리적 충돌 명시"를 시스템 프롬프트로 지시.
  4. AI가 JSON 포맷(`keyPoint`, `shortSummary`, `longSummary`)으로 병합된 요약본을 반환.
  5. 반환된 요약본을 바탕으로 `toBranch`에 새로운 병합 커밋(Merge Commit) 생성.

## 3. 프론트엔드 연동 계획 (UI)
- **위치:** `WorkspacePage.vue` 또는 `ChatPage.vue`의 사이드바.
- **기능:** 
  1. [Merge Branch] 버튼 추가.
  2. 소스 브랜치(`from`)와 타겟 브랜치(`to`)를 선택할 수 있는 모달 UI 제공.
  3. (Pro/Master 전용) 병합 시 AI에게 전달할 '사용자 가이드 코멘트' 입력 란 제공.
  4. 머지 완료 후 자동으로 타겟 브랜치로 화면 이동 및 그래프(분기선 합류) 시각화 갱신.

## 4. 작업 순서 (Action Items)
1. **백엔드:** `MergeSummaryAiService` 작성 (Prompt 및 OpenAI 연동).
2. **백엔드:** `MergeService.kt` 리팩토링 (공통 조상 탐색, 플랜별 분기 처리, Squash 및 Deep Merge 구현).
3. **프론트엔드:** Merge 모달 UI 컴포넌트 개발 및 API 연동.
4. **검증:** 분기 후 대화 진행 -> 머지 요청 -> 생성된 머지 커밋의 내용 및 그래프 확인.