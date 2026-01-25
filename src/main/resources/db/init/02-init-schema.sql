-- GaitProject 초기 스키마 (docs/db_schema.md 기반)
-- 이 파일은 Docker(Postgres) 컨테이너 최초 초기화 시 자동 실행됩니다.
-- (주의) /var/lib/postgresql/data 볼륨이 이미 존재하면 재실행되지 않습니다.

-- UUID 생성용 (gen_random_uuid)
CREATE EXTENSION IF NOT EXISTS pgcrypto;
-- pgvector
CREATE EXTENSION IF NOT EXISTS vector;

-- ---------- ENUM TYPES ----------
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'plan_type') THEN
    CREATE TYPE plan_type AS ENUM ('FREE', 'STANDARD', 'MASTER');
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'message_role') THEN
    CREATE TYPE message_role AS ENUM ('USER', 'ASSISTANT', 'SYSTEM', 'TOOL');
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'merge_type') THEN
    CREATE TYPE merge_type AS ENUM ('NONE', 'FAST_FORWARD', 'SQUASH', 'DEEP');
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'credit_event_type') THEN
    CREATE TYPE credit_event_type AS ENUM ('RAG_AUTO', 'RAG_MANUAL', 'PREMIUM_CHAT', 'SUMMARY', 'OTHER');
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'subscription_status') THEN
    CREATE TYPE subscription_status AS ENUM ('ACTIVE', 'CANCELLED', 'EXPIRED');
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'payment_status') THEN
    CREATE TYPE payment_status AS ENUM ('PENDING', 'PAID', 'FAILED', 'REFUNDED');
  END IF;
END $$;

-- ---------- TABLES ----------
CREATE TABLE IF NOT EXISTS users (
  id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  email                   VARCHAR(255) NOT NULL UNIQUE,
  name                    VARCHAR(100),
  plan                    plan_type NOT NULL DEFAULT 'FREE',
  premium_wallet_cents    BIGINT NOT NULL DEFAULT 0,
  cheap_tokens_used       BIGINT NOT NULL DEFAULT 0,
  rag_calls_today         INTEGER NOT NULL DEFAULT 0,
  rag_daily_limit         INTEGER NOT NULL DEFAULT 0,
  timezone                VARCHAR(50) DEFAULT 'Asia/Seoul',
  locale                  VARCHAR(10) DEFAULT 'ko-KR',
  created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  deleted_at              TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_users_plan ON users(plan);

CREATE TABLE IF NOT EXISTS user_subscriptions (
  id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id             UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  plan                plan_type NOT NULL,
  status              subscription_status NOT NULL DEFAULT 'ACTIVE',
  starts_at           TIMESTAMPTZ NOT NULL,
  ends_at             TIMESTAMPTZ NOT NULL,
  payment_provider    VARCHAR(50),
  external_sub_id     VARCHAR(255),
  memo                TEXT,
  created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_user_subscriptions_user_status
  ON user_subscriptions(user_id, status);

CREATE TABLE IF NOT EXISTS payment_transactions (
  id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id             UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  subscription_id     UUID REFERENCES user_subscriptions(id),
  amount_cents        BIGINT NOT NULL,
  currency            VARCHAR(10) NOT NULL DEFAULT 'KRW',
  status              payment_status NOT NULL DEFAULT 'PENDING',
  payment_provider    VARCHAR(50),
  external_payment_id VARCHAR(255),
  description         TEXT,
  created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_payment_transactions_user_created
  ON payment_transactions(user_id, created_at);

CREATE TABLE IF NOT EXISTS user_monthly_usage (
  id                  BIGSERIAL PRIMARY KEY,
  user_id             UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  year_month          DATE NOT NULL,
  premium_tokens_used BIGINT NOT NULL DEFAULT 0,
  cheap_tokens_used   BIGINT NOT NULL DEFAULT 0,
  rag_calls           BIGINT NOT NULL DEFAULT 0,
  created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE (user_id, year_month)
);

CREATE INDEX IF NOT EXISTS idx_user_monthly_usage_user_month
  ON user_monthly_usage(user_id, year_month);

CREATE TABLE IF NOT EXISTS workspaces (
  id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id             UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  name                VARCHAR(100) NOT NULL,
  description         TEXT,
  default_branch_id   UUID,
  is_archived         BOOLEAN NOT NULL DEFAULT FALSE,
  created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  deleted_at          TIMESTAMPTZ
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_workspaces_user_name_active
  ON workspaces (user_id, name)
  WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS branches (
  id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  workspace_id        UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
  name                VARCHAR(100) NOT NULL,
  description         TEXT,
  head_commit_id      UUID,
  base_commit_id      UUID,
  is_default          BOOLEAN NOT NULL DEFAULT FALSE,
  is_archived         BOOLEAN NOT NULL DEFAULT FALSE,
  created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  deleted_at          TIMESTAMPTZ,
  CONSTRAINT uq_branches_workspace_name UNIQUE (workspace_id, name)
);

CREATE TABLE IF NOT EXISTS commits (
  id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  workspace_id        UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
  branch_id           UUID NOT NULL REFERENCES branches(id) ON DELETE CASCADE,
  parent_id           UUID REFERENCES commits(id),
  merge_parent_id     UUID REFERENCES commits(id),
  merge_type          merge_type NOT NULL DEFAULT 'NONE',
  is_merge            BOOLEAN NOT NULL DEFAULT FALSE,
  created_by_user_id  UUID REFERENCES users(id),
  key_point           VARCHAR(255) NOT NULL,
  short_summary       TEXT,
  long_summary        TEXT,
  embedding           VECTOR(1536),
  input_tokens        INTEGER,
  output_tokens       INTEGER,
  model_name          VARCHAR(100),
  created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  deleted_at          TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_commits_branch_created
  ON commits(branch_id, created_at);

CREATE INDEX IF NOT EXISTS idx_commits_embedding
  ON commits USING hnsw (embedding vector_cosine_ops);

CREATE TABLE IF NOT EXISTS messages (
  id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  workspace_id        UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
  branch_id           UUID NOT NULL REFERENCES branches(id) ON DELETE CASCADE,
  commit_id           UUID REFERENCES commits(id),
  user_id             UUID REFERENCES users(id),
  role                message_role NOT NULL,
  content             TEXT NOT NULL,
  metadata            JSONB,
  sequence            BIGINT NOT NULL,
  embedding           VECTOR(1536),
  input_tokens        INTEGER,
  output_tokens       INTEGER,
  model_name          VARCHAR(100),
  created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  deleted_at          TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_messages_branch_sequence
  ON messages(branch_id, sequence);
CREATE INDEX IF NOT EXISTS idx_messages_branch_created
  ON messages(branch_id, created_at);

CREATE INDEX IF NOT EXISTS idx_messages_embedding
  ON messages USING ivfflat (embedding vector_cosine_ops)
  WITH (lists = 100);

CREATE TABLE IF NOT EXISTS merges (
  id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  workspace_id         UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
  from_branch_id       UUID NOT NULL REFERENCES branches(id) ON DELETE CASCADE,
  to_branch_id         UUID NOT NULL REFERENCES branches(id) ON DELETE CASCADE,
  from_commit_id       UUID REFERENCES commits(id),
  to_commit_id         UUID REFERENCES commits(id),
  merge_commit_id      UUID REFERENCES commits(id),
  merge_type           merge_type NOT NULL,
  initiated_by_user_id UUID REFERENCES users(id),
  notes                TEXT,
  created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_merges_workspace_created
  ON merges(workspace_id, created_at);

CREATE TABLE IF NOT EXISTS credit_logs (
  id                BIGSERIAL PRIMARY KEY,
  user_id           UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  workspace_id      UUID REFERENCES workspaces(id) ON DELETE SET NULL,
  branch_id         UUID REFERENCES branches(id) ON DELETE SET NULL,
  event_type        credit_event_type NOT NULL,
  description       TEXT,
  model_name        VARCHAR(100),
  input_tokens      INTEGER,
  output_tokens     INTEGER,
  amount_cents      BIGINT,
  rag_credits_delta INTEGER,
  created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_credit_logs_user_created
  ON credit_logs(user_id, created_at);
CREATE INDEX IF NOT EXISTS idx_credit_logs_type_created
  ON credit_logs(event_type, created_at);

CREATE TABLE IF NOT EXISTS plans (
  id                      SERIAL PRIMARY KEY,
  code                    plan_type NOT NULL UNIQUE,
  name                    VARCHAR(50) NOT NULL,
  description             TEXT,
  input_limit_tokens      INTEGER NOT NULL,
  output_limit_tokens     INTEGER NOT NULL,
  cheap_pool_monthly      BIGINT NOT NULL,
  premium_wallet_monthly  BIGINT NOT NULL,
  rag_daily_limit_default INTEGER NOT NULL,
  created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ---------- LATE FK ----------
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM pg_constraint
    WHERE conname = 'fk_workspaces_default_branch'
  ) THEN
    ALTER TABLE workspaces
      ADD CONSTRAINT fk_workspaces_default_branch
      FOREIGN KEY (default_branch_id) REFERENCES branches(id);
  END IF;
END $$;

