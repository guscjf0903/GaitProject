#!/bin/bash

# DB(Postgres+pgvector) 실행 스크립트 (단일 진입점)
# 사용법:
#   ./scripts/db.sh up local
#   ./scripts/db.sh down local
#   ./scripts/db.sh logs local
#
# dev/prod도 동일하게 local 대신 dev/prod로 실행

set -euo pipefail

CMD=${1:-up}      # up | down | logs | ps | reset
ENV_TYPE=${2:-local}

# 스크립트를 어디서 실행해도 항상 프로젝트 루트를 기준으로 동작하도록 고정
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

ENV_FILE="${PROJECT_ROOT}/config/.env.${ENV_TYPE}"
COMPOSE_FILE="${PROJECT_ROOT}/docker-compose.yml"

if [ ! -f "$ENV_FILE" ]; then
  echo "❌ ${ENV_FILE} 파일을 찾을 수 없습니다. (local|dev|prod)"
  exit 1
fi

if [ ! -f "$COMPOSE_FILE" ]; then
  echo "❌ ${COMPOSE_FILE} 파일을 찾을 수 없습니다."
  exit 1
fi

case "$CMD" in
  up)
    docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" up -d
    ;;
  down)
    docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" down
    ;;
  reset)
    # ⚠️ 모든 데이터 삭제: docker volume(postgres_data)까지 제거 후 재생성
    docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" down -v
    docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" up -d
    ;;
  logs)
    docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" logs -f postgres
    ;;
  ps)
    docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" ps
    ;;
  *)
    echo "❌ 지원하지 않는 명령: $CMD (up|down|reset|logs|ps)"
    exit 1
    ;;
esac


