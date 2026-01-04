#!/bin/bash

# Spring Boot + Kotlin 프로젝트 기본 폴더 구조 생성 스크립트

BASE_DIR="src/main"
TEST_DIR="src/test"

echo "📁 프로젝트 폴더 구조 생성 중..."

# Main 소스 디렉토리
mkdir -p "${BASE_DIR}/kotlin/com/gait/gaitproject"
mkdir -p "${BASE_DIR}/resources"

# Test 소스 디렉토리
mkdir -p "${TEST_DIR}/kotlin/com/gait/gaitproject"
mkdir -p "${TEST_DIR}/resources"

# Resources 하위 폴더
mkdir -p "${BASE_DIR}/resources/static"
mkdir -p "${BASE_DIR}/resources/templates"
mkdir -p "${BASE_DIR}/resources/db/migration"  # Flyway용 (선택사항)

echo "✅ 폴더 구조 생성 완료!"
echo ""
echo "생성된 구조:"
echo "  ${BASE_DIR}/kotlin/com/gait/gaitproject/"
echo "  ${BASE_DIR}/resources/"
echo "  ${TEST_DIR}/kotlin/com/gait/gaitproject/"
echo "  ${TEST_DIR}/resources/"
