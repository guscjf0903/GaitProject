# AI 연동 설정 가이드

## 개요

본 프로젝트는 Spring AI를 사용하여 Gemini Flash/Pro와 OpenAI GPT-4o를 연동합니다.
설계서 5.1 기준으로 플랜별 AI 모델이 자동으로 선택됩니다.

## 설정 방법

### 1. Stub 모드 (기본값, 개발/테스트용)

AI 연동 없이 테스트하려면:

```yaml
# application.yml 또는 application-local.yml
app:
  ai:
    use-stub: true
```

이 경우 `StubAiService`가 사용되어 실제 AI 호출 없이 동작합니다.

### 2. Gemini Flash 연동

#### 2.1 Google Cloud 프로젝트 설정

1. [Google Cloud Console](https://console.cloud.google.com/)에서 프로젝트 생성
2. Vertex AI API 활성화
3. 서비스 계정 생성 및 JSON 키 다운로드

#### 2.2 환경변수 설정

```bash
export GOOGLE_PROJECT_ID=your-project-id
export GOOGLE_LOCATION=us-central1
export GOOGLE_CREDENTIALS_PATH=/path/to/service-account-key.json
export GEMINI_ENABLED=true
```

또는 `application-local.yml`:

```yaml
spring:
  ai:
    vertex:
      ai:
        gemini:
          project-id: ${GOOGLE_PROJECT_ID}
          location: ${GOOGLE_LOCATION:us-central1}
          credentials:
            location: ${GOOGLE_CREDENTIALS_PATH}
          chat:
            options:
              model: gemini-1.5-flash
              temperature: 0.7
          enabled: true
```

### 3. Gemini Pro 연동

MASTER 플랜에서 사용하는 고성능 모델:

```yaml
spring:
  ai:
    vertex:
      ai:
        gemini:
          pro:
            enabled: true
            chat:
              options:
                model: gemini-1.5-pro
                temperature: 0.7
```

### 4. OpenAI GPT-4o 연동

```bash
export OPENAI_API_KEY=sk-...
export OPENAI_MODEL=gpt-4o
```

또는 `application-local.yml`:

```yaml
spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
      chat:
        options:
          model: gpt-4o
          temperature: 0.7
```

## 플랜별 AI 모델 매핑

| 플랜 | 일반 채팅 | 커밋 요약 | 머지 |
|------|----------|---------|------|
| FREE | Gemini Flash | Gemini Flash | 불가 |
| STANDARD | Gemini Flash | Gemini Flash | Fast-forward |
| MASTER | Gemini Pro / GPT-4o | Gemini Flash | Deep Merge (Pro) |

## 토큰 예산 관리

설계서 2.1 기준으로 플랜별 토큰 예산이 자동 할당됩니다:

- **FREE**: Input 4k, Output 800
- **STANDARD**: Input 8k, Output 1.5k
- **MASTER**: Input 16k, Output 2.5k

컨텍스트는 다음 비율로 구성됩니다:
- System & Query: ~10%
- Recent History: ~50%
- Head Context: ~25%
- Lineage History: ~15%

## 테스트

### 1. Stub 모드 테스트

```bash
# application-local.yml에 설정
app:
  ai:
    use-stub: true

# 서버 실행
./gradlew bootRun
```

### 2. 실제 AI 연동 테스트

```bash
# 환경변수 설정 후
export GEMINI_ENABLED=true
export GOOGLE_PROJECT_ID=your-project-id
export GOOGLE_CREDENTIALS_PATH=/path/to/key.json

# 서버 실행
./gradlew bootRun
```

### 3. API 테스트

Swagger UI에서 테스트:
- http://localhost:8080/swagger-ui/index.html
- `/api/chat/stream` 엔드포인트 호출

또는 curl:

```bash
curl -X POST http://localhost:8080/api/chat/stream \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -d '{
    "workspaceId": "workspace-uuid",
    "branchId": "branch-uuid",
    "content": "안녕하세요!"
  }'
```

## 문제 해결

### 1. "AI 서비스가 설정되지 않았습니다" 오류

- `app.ai.use-stub=true`로 설정하거나
- Gemini/OpenAI 설정이 올바른지 확인

### 2. Gemini 인증 오류

- `GOOGLE_CREDENTIALS_PATH`가 올바른지 확인
- 서비스 계정에 Vertex AI 권한이 있는지 확인

### 3. 토큰 예산 초과

- 플랜별 Input/Output 상한 확인
- ContextBuilder가 토큰 예산을 초과하지 않도록 자동 조정됨


