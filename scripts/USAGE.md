# Scripts 사용법

이 폴더의 스크립트는 **환경(local/dev/prod)** 별로 동일한 명령 형태로 실행되도록 구성되어 있습니다.

## 사전 준비

- 민감 정보(DB 비밀번호 등)는 **반드시** `config/.env.*` 파일에만 두세요. (Git에 커밋되지 않도록 `.gitignore` 처리됨)
- 새로 클론한 환경에서는 아래처럼 예시 파일을 복사해서 시작하세요:

```bash
cp config/env.local.example config/.env.local
```

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

# (권장) DB 완전 초기화: 볼륨까지 삭제 후 재생성 (스키마/데이터 모두 삭제)
./scripts/db.sh reset local
```

> `local` 대신 `dev` 또는 `prod`로 바꾸면 해당 환경의 `config/.env.{env}` 값을 사용합니다.

## 참고

- Spring Profile 선택은 `SPRING_PROFILES_ACTIVE` 로 결정됩니다.
- `application-local.yml`, `application-dev.yml`, `application-prod.yml` 안의 `${...}` 값은 위 env 파일에서 **런타임에 치환**됩니다.

## DB 스키마가 “항상 동일”하게 만들어지는 방식

- 이 프로젝트는 `docker-compose.yml`에서 `src/main/resources/db/init/`를 Postgres 초기화 폴더(`/docker-entrypoint-initdb.d`)로 마운트합니다.
- 그래서 **DB 볼륨이 비어있는 최초 1회**에 한해 아래 SQL들이 자동 실행됩니다.
  - `01-init-pgvector.sql`: pgvector 확장 활성화
  - `02-init-schema.sql`: 테이블/컬럼/인덱스/ENUM 전체 생성
- 이미 DB가 떠 있는 상태에서 스키마 SQL을 바꿨다면, 다시 적용하려면 `reset`을 사용하세요.


