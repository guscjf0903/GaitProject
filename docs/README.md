# Gait Project 문서

이 폴더는 **기획/스키마/가이드/진단/트러블슈팅/개발일지**를 분리해서 관리합니다.  
문서는 앞으로도 주기적으로 계속 쌓이므로, 아래 구조와 파일 네이밍 규칙을 기준으로 업데이트합니다.

---

## 폴더 구조

- `planning/`: 기획/설계 문서
  - `project_plan.md`
- `schema/`: DB/데이터 스키마
  - `db_schema.md`
- `guides/`: 설정/운영 가이드
  - `AI_SETUP.md`
- `implementation/`: 구현 현황/체크리스트
  - `implementation_status.md`
- `diagnostics/`: E2E 진단/재현/로그 기반 분석
  - `2026-02-e2e-refresh-missing-messages.md`
- `troubleshooting/`: 트러블슈팅 리포트(월별 누적)
  - `YYYY-MM.md` (예: `2026-02.md`)
  - `TEMPLATE.md`
  - `archive/` (정리 완료된 오래된 문서 보관)
- `development_log/`: 개발 일지(월별 누적)
  - `YYYY-MM.md` (예: `2026-02.md`)
  - `TEMPLATE.md`

---

## 월별 누적 규칙(권장)

- **트러블슈팅**: `troubleshooting/YYYY-MM.md`
  - 신규 이슈가 생기면 해당 월 파일에 섹션을 추가
  - 큰 이슈는 **재현 → 원인 → 해결 → 회귀 테스트** 형식 유지

- **개발일지**: `development_log/YYYY-MM.md`
  - 기능 단위로 **한 일 / 결정 / 다음 할 일**을 짧게 누적

---

## 바로가기

- **기획서**: `planning/project_plan.md`
- **DB 스키마**: `schema/db_schema.md`
- **구현 현황**: `implementation/implementation_status.md`
- **AI 설정 가이드**: `guides/AI_SETUP.md`
- **E2E 진단(새로고침 대화 사라짐)**: `diagnostics/2026-02-e2e-refresh-missing-messages.md`
- **트러블슈팅(2026-02)**: `troubleshooting/2026-02.md`
- **개발일지(2026-02)**: `development_log/2026-02.md`
