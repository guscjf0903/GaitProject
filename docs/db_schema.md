```sql
-- pgvector 확장
CREATE EXTENSION IF NOT EXISTS vector;

-- 공통 enum 대신, 일단 텍스트 + CHECK 로 갈 수도 있지만,
-- 여기서는 enum으로 예시 (나중에 바꾸고 싶으면 lookup table로 분리해도 됨)

CREATE TYPE plan_type AS ENUM ('FREE', 'STANDARD', 'MASTER');

CREATE TYPE message_role AS ENUM ('USER', 'ASSISTANT', 'SYSTEM', 'TOOL');

CREATE TYPE merge_type AS ENUM ('NONE', 'FAST_FORWARD', 'SQUASH', 'DEEP');

CREATE TYPE credit_event_type AS ENUM (
  'RAG_AUTO',
  'RAG_MANUAL',
  'PREMIUM_CHAT',
  'SUMMARY',
  'OTHER'
);

--------------------------User-----------------------------
CREATE TABLE users (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email               VARCHAR(255) NOT NULL UNIQUE,
    name                VARCHAR(100),
    -- 플랜 기본 정보
    plan                plan_type NOT NULL DEFAULT 'FREE',
    -- 프리미엄 지갑 (원 단위, 또는 포인트 단위)
    premium_wallet_cents    BIGINT NOT NULL DEFAULT 0,
    -- cheap pool 사용 토큰 수 (월별 리셋 전까지 누적)
    cheap_tokens_used       BIGINT NOT NULL DEFAULT 0,
    -- RAG 일일 사용량 (일 기준 리셋)
    rag_calls_today         INTEGER NOT NULL DEFAULT 0,
    rag_daily_limit         INTEGER NOT NULL DEFAULT 0, -- 플랜별 기본값 or 동적 설정
    -- 기타 메타
    timezone            VARCHAR(50) DEFAULT 'Asia/Seoul',
    locale              VARCHAR(10) DEFAULT 'ko-KR',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at          TIMESTAMPTZ
);

CREATE INDEX idx_users_plan ON users(plan);

-----------------------User 플랜, 업그레이드 관련 테이블---------------------
CREATE TYPE subscription_status AS ENUM ('ACTIVE', 'CANCELLED', 'EXPIRED');

CREATE TABLE user_subscriptions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    plan            plan_type NOT NULL,
    status          subscription_status NOT NULL DEFAULT 'ACTIVE',

    starts_at       TIMESTAMPTZ NOT NULL,
    ends_at         TIMESTAMPTZ NOT NULL, -- 1개월/1년 구독 만료일

    -- 결제/플랫폼 연동용
    payment_provider    VARCHAR(50), -- 'TOSS', 'INICIS', 'TEST' 등
    external_sub_id     VARCHAR(255), -- PG사 쪽 구독 ID
    memo            TEXT,

    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_user_subscriptions_user_status
    ON user_subscriptions(user_id, status);

------------------결재 이력 -----------------------------
CREATE TYPE payment_status AS ENUM ('PENDING', 'PAID', 'FAILED', 'REFUNDED');

CREATE TABLE payment_transactions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    subscription_id UUID REFERENCES user_subscriptions(id),

    amount_cents    BIGINT NOT NULL,
    currency        VARCHAR(10) NOT NULL DEFAULT 'KRW',

    status          payment_status NOT NULL DEFAULT 'PENDING',

    payment_provider    VARCHAR(50),   -- PG사
    external_payment_id VARCHAR(255),  -- PG사 결제 ID

    description     TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_payment_transactions_user_created
    ON payment_transactions(user_id, created_at);

------------------------월별 사용량 집계 -----------------------
CREATE TABLE user_monthly_usage (
    id                      BIGSERIAL PRIMARY KEY,
    user_id                 UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    year_month              DATE NOT NULL, -- 예: 2025-12-01 형태 (해당 월의 1일)
    premium_tokens_used     BIGINT NOT NULL DEFAULT 0,
    cheap_tokens_used       BIGINT NOT NULL DEFAULT 0,
    rag_calls               BIGINT NOT NULL DEFAULT 0,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, year_month)
);

CREATE INDEX idx_user_monthly_usage_user_month
    ON user_monthly_usage(user_id, year_month);
    
------------------------workspace, branch, commit 관련 -----------------------
CREATE TABLE workspaces (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name                VARCHAR(100) NOT NULL,
    description         TEXT,
    default_branch_id   UUID, -- branches(id) 참조, 생성 후 업데이트

    is_archived         BOOLEAN NOT NULL DEFAULT FALSE,

    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at          TIMESTAMPTZ,

);

CREATE UNIQUE INDEX uq_workspaces_user_name_active 
ON workspaces (user_id, name) 
WHERE deleted_at IS NULL;

CREATE TABLE branches (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id        UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    name                VARCHAR(100) NOT NULL,
    description         TEXT,

    head_commit_id      UUID, -- commits(id)
    base_commit_id      UUID, -- 이 브랜치가 어디 커밋에서 분기됐는지, null이면 root

    is_default          BOOLEAN NOT NULL DEFAULT FALSE,
    is_archived         BOOLEAN NOT NULL DEFAULT FALSE,

    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at          TIMESTAMPTZ,

    CONSTRAINT uq_branches_workspace_name UNIQUE (workspace_id, name)
);

-- 나중에 FK 추가 (테이블 순서 때문에 나중에 ALTER로 걸어도 됨)
-- ALTER TABLE workspaces
--   ADD CONSTRAINT fk_workspaces_default_branch
--   FOREIGN KEY (default_branch_id) REFERENCES branches(id);

CREATE TABLE commits (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    workspace_id        UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    branch_id           UUID NOT NULL REFERENCES branches(id) ON DELETE CASCADE,

    parent_id           UUID REFERENCES commits(id),
    merge_parent_id     UUID REFERENCES commits(id), -- 머지 시 두 번째 부모
    merge_type          merge_type NOT NULL DEFAULT 'NONE',
    is_merge            BOOLEAN NOT NULL DEFAULT FALSE,

    created_by_user_id  UUID REFERENCES users(id),

    -- 요약 정보
    key_point           VARCHAR(255) NOT NULL, -- 제목/핵심
    short_summary       TEXT,                  -- 3~4문장 요약
    long_summary        TEXT,                  -- 상세 요약 (Lineage/Head 컨텍스트용)

    -- 임베딩 (pgvector, 차원은 모델에 맞게 조정: 예 1536)
    embedding           VECTOR(1536),

    -- 토큰/비용 메타 (선택)
    input_tokens        INTEGER,
    output_tokens       INTEGER,
    model_name          VARCHAR(100),

    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at          TIMESTAMPTZ
);

-- 브랜치별 타임라인 조회용
CREATE INDEX idx_commits_branch_created
    ON commits(branch_id, created_at);

-- 벡터 검색용 (hnsw)
CREATE INDEX idx_commits_embedding 
	ON commits USING hnsw (embedding vector_cosine_ops);
    
    
CREATE TABLE messages (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    workspace_id        UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    branch_id           UUID NOT NULL REFERENCES branches(id) ON DELETE CASCADE,
    commit_id           UUID REFERENCES commits(id), -- null이면 아직 커밋 안 된 메시지

    user_id             UUID REFERENCES users(id), -- USER role일 때 작성자, ASSISTANT면 null 가능
    role                message_role NOT NULL,

    content             TEXT NOT NULL,
    metadata            JSONB, -- tool 호출 정보, function name, arguments 등

    -- 대화 내 순서 (브랜치 기준 incremental)
    sequence            BIGINT NOT NULL, -- branch 내에서 증가
    -- 임베딩 (선택: 커밋만 임베딩할 수도 있고, 최근 메시지만 임베딩할 수도 있음)
    embedding           VECTOR(1536),

    input_tokens        INTEGER,
    output_tokens       INTEGER,
    model_name          VARCHAR(100),

    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at          TIMESTAMPTZ
);

-- 브랜치 타임라인 조회
CREATE INDEX idx_messages_branch_sequence
    ON messages(branch_id, sequence);

CREATE INDEX idx_messages_branch_created
    ON messages(branch_id, created_at);

-- 벡터 검색용
CREATE INDEX idx_messages_embedding
    ON messages USING ivfflat (embedding vector_cosine_ops)
    WITH (lists = 100);
    

----------------------------Merge------------------------
CREATE TABLE merges (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    workspace_id        UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    from_branch_id      UUID NOT NULL REFERENCES branches(id) ON DELETE CASCADE,
    to_branch_id        UUID NOT NULL REFERENCES branches(id) ON DELETE CASCADE,

    -- 어떤 커밋 범위를 머지했는지 (옵션)
    from_commit_id      UUID REFERENCES commits(id),
    to_commit_id        UUID REFERENCES commits(id),

    merge_commit_id     UUID REFERENCES commits(id), -- 결과 머지 커밋
    merge_type          merge_type NOT NULL,
    initiated_by_user_id UUID REFERENCES users(id),

    notes               TEXT, -- 사용자 코멘트 or AI가 생성한 충돌 해설 등

    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_merges_workspace_created
    ON merges(workspace_id, created_at);

```

```sql
--------------------------credit--------------------------------------
CREATE TABLE credit_logs (
    id                  BIGSERIAL PRIMARY KEY,

    user_id             UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    workspace_id        UUID REFERENCES workspaces(id) ON DELETE SET NULL,
    branch_id           UUID REFERENCES branches(id) ON DELETE SET NULL,

    event_type          credit_event_type NOT NULL,
    description         TEXT, -- "AUTO_RAG", "Commit summary", "Master chat" 등 자유롭게

    -- 토큰/모델 정보
    model_name          VARCHAR(100),
    input_tokens        INTEGER,
    output_tokens       INTEGER,
    -- 금액 관련
    amount_cents        BIGINT, -- 프리미엄 지갑에서 차감된 금액(양수) or 충전(음수)
    -- RAG 크레딧 변경량 (예: -1)
    rag_credits_delta   INTEGER,

    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_credit_logs_user_created
    ON credit_logs(user_id, created_at);

CREATE INDEX idx_credit_logs_type_created
    ON credit_logs(event_type, created_at);
    
--------------------------Plans-------------------------------------------
CREATE TABLE plans (
    id                  SERIAL PRIMARY KEY,
    code                plan_type NOT NULL UNIQUE,
    name                VARCHAR(50) NOT NULL,
    description         TEXT,

    input_limit_tokens      INTEGER NOT NULL,
    output_limit_tokens     INTEGER NOT NULL,
    cheap_pool_monthly      BIGINT NOT NULL,
    premium_wallet_monthly  BIGINT NOT NULL,
    rag_daily_limit_default INTEGER NOT NULL,

    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

```