#!/bin/bash

# 앱 실행 스크립트 (단일 진입점)
# 사용법:
#   ./scripts/run.sh local
#   ./scripts/run.sh dev
#   ./scripts/run.sh prod

set -euo pipefail

ENV_TYPE=${1:-local}
ENV_FILE="config/.env.${ENV_TYPE}"

if [ ! -f "$ENV_FILE" ]; then
  echo "❌ ${ENV_FILE} 파일을 찾을 수 없습니다. (local|dev|prod)"
  exit 1
fi

export $(grep -v '^[[:space:]]*#' "$ENV_FILE" | grep -v '^[[:space:]]*$' | xargs)

echo "✅ 환경 로드: ${ENV_FILE} (SPRING_PROFILES_ACTIVE=${SPRING_PROFILES_ACTIVE:-})"
echo "🚀 앱 실행: ./gradlew bootRun"

./gradlew bootRun


