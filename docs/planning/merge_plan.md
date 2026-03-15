# 머지(Merge) 기능 구현 계획서

작성일: 2026-03-01  
갱신일: 2026-03-15  
관련 기획서: `docs/planning/project_plan.md` (4.5 머지 프로세스)

## 1. 목적 및 현황
- **목표:** 분기된 대화(브랜치)의 내용을 병합하여 지식을 통합하는 기능 구현. 사용자 플랜에 따라 병합의 수준(저비용 AI 요약 vs 고비용 AI 지능형 통합)을 다르게 제공.
- **현재 구현 상태:** `FAST_FORWARD` 제거 완료. `SQUASH`(저비용 AI 요약)와 `DEEP`(고비용 AI 지능형 통합) 이중 모드로 리팩토링 완료.

## 2. 플랜별 머지 정책

### 2.1 Free 플랜
- **정책:** 머지 기능 사용 불가.

### 2.2 Standard 플랜 — Squash Merge
- **정책:** 저비용 AI 요약 통합. 입력 상한 2,000토큰 (원본 20% / short 50% / long 30%).
- **구현:** `MergeContextBuilder`가 공통 조상 이후의 fromPath/toPath 재료를 수집 → `MergeSummaryAiService.summarizeSquash` → 새 merge commit 생성.

### 2.3 Master 플랜 — Deep Merge
- **정책:** 고비용 AI 지능형 통합. 입력 상한 6,000토큰 (원본 40% / short 20% / long 40%). 겹치는 내용, 보강 포인트, 타임라인, 충돌 분석까지 포함.
- **구현:** 동일 파이프라인, 예산과 프롬프트만 다르게 적용 → `MergeSummaryAiService.summarizeDeep`.

## 3. 백엔드 구조
- `MergeContextBuilder.kt`: 공통 조상 탐색, fromPath/toPath 수집, 메시지/요약 재료 추출, 토큰 예산 분배.
- `MergeSummaryAiService.kt`: `summarizeSquash` / `summarizeDeep` 이중 모드. 모델/온도/maxTokens 분리.
- `MergeService.kt`: 정책 검증 → 컨텍스트 빌드 → AI 요약 → merge commit 저장.
- `MergeDtos.kt`: `MergeResponse`에 `fromBranchName`, `toBranchName` 추가.

## 4. 프론트엔드 구현
- **그래프:** `mergeParentId`를 이용한 merge 엣지(점선) 렌더링. Merge 노드는 다이아몬드 형태.
- **타임라인:** merge commit을 `merge summary card`로 표시 — 타입 뱃지, keyPoint, shortSummary, 펼치기(longSummary).
- **모달:** FAST_FORWARD 제거, SQUASH/DEEP 설명 표시, notes 입력 공통 제공.

## 5. 검증 시나리오
1. 분기 후 STANDARD에서 Squash → 새 merge commit 생성, 그래프 merge 선 표시, 타임라인 카드 표시.
2. MASTER에서 Deep → AI 통합 결과가 longSummary에 충분히 반영되는지 확인.
3. 머지 후 target branch에서 후속 채팅/커밋이 자연스럽게 이어지는지 확인.
4. 동일 브랜치 머지, 이미 최신 상태, 플랜 미허용 등 실패 케이스 메시지 확인.
