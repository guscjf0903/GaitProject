#!/bin/bash

# DB(Postgres+pgvector) 실행 스크립트 (단일 진입점)
# 사용법:
#   ./scripts/db.sh up local
#   ./scripts/db.sh down local
#   ./scripts/db.sh logs local
#
# dev/prod도 동일하게 local 대신 dev/prod로 실행

set -euo pipefail

CMD=${1:-up}      # up | down | logs | ps
ENV_TYPE=${2:-local}
ENV_FILE="config/.env.${ENV_TYPE}"

if [ ! -f "$ENV_FILE" ]; then
  echo "❌ ${ENV_FILE} 파일을 찾을 수 없습니다. (local|dev|prod)"
  exit 1
fi

case "$CMD" in
  up)
    docker compose --env-file "$ENV_FILE" up -d
    ;;
  down)
    docker compose --env-file "$ENV_FILE" down
    ;;
  logs)
    docker compose --env-file "$ENV_FILE" logs -f postgres
    ;;
  ps)
    docker compose --env-file "$ENV_FILE" ps
    ;;
  *)
    echo "❌ 지원하지 않는 명령: $CMD (up|down|logs|ps)"
    exit 1
    ;;
esac


