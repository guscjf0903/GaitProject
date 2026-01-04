# Scripts 사용법

이 폴더의 스크립트는 **환경(local/dev/prod)** 별로 동일한 명령 형태로 실행되도록 구성되어 있습니다.

## 사전 준비

- `config/.env.local`, `config/.env.dev`, `config/.env.prod` 에서 값을 수정하세요.
- 민감 정보(DB 비밀번호 등)는 **반드시** `config/.env.*` 파일에만 두세요. (Git에 커밋되지 않도록 `.gitignore` 처리됨)

## 앱 실행 (Spring Boot)

```bash
# local
./scripts/run.sh local

# dev
./scripts/run.sh dev

# prod
./scripts/run.sh prod
```

## DB 실행 (Postgres + pgvector, docker compose)

```bash
# DB 올리기
./scripts/db.sh up local

# 상태 보기
./scripts/db.sh ps local

# 로그 보기
./scripts/db.sh logs local

# 내리기
./scripts/db.sh down local
```

> `local` 대신 `dev` 또는 `prod`로 바꾸면 해당 환경의 `config/.env.{env}` 값을 사용합니다.

## 참고

- Spring Profile 선택은 `SPRING_PROFILES_ACTIVE` 로 결정됩니다.
- `application-local.yml`, `application-dev.yml`, `application-prod.yml` 안의 `${...}` 값은 위 env 파일에서 **런타임에 치환**됩니다.


