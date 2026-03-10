# **🏗️ Git 기반 AI 채팅 서비스 시스템 설계서 (v3.0 종합 정리본)**

## **0. 목적 & 컨셉**

- **목표**
    - LLM 대화를 **Git처럼 관리**해서
        - 브랜치별로 맥락이 섞이지 않게 유지
        - 대화 흐름을 커밋 단위로 요약/저장
        - 브랜치 간 **Merge** 로 지식 통합
    - 동시에 **토큰/비용 효율성을 극대화**하면서 **사용자 경험(UX)** 을 살리는 AI 채팅 서비스 구축
- **핵심 컨셉**
    - Git 구조를 차용한 AI 대화 관리
        - Workspace / Branch / Commit / Message
    - **컨텍스트 동적 구성 + RAG + 플랜별 제약** 을 합친 “AI-코딩 협업 툴” 스타일 서비스
    

## **1. 시스템 개요 및 아키텍처**

### **1.1 핵심 가치(Key Values)**

1. **Git 구조화**
    - **Branch**: 대화의 분기. 주제/기능별로 다른 브랜치 유지.
    - **Commit**: 일정 턴(예: 20턴)마다 대화 내용을 요약해 저장.
    - **Merge**: 브랜치 간 내용을 통합해서 상위 개념/최종 정리본 생성.
2. **비용 효율성**
    - **Gemini Flash**: 일반 채팅/요약/검색쿼리 생성 등 “대량/저비용” 작업 전담.
    - **Gemini Pro / GPT-4o 등..**: Master 플랜의 고난도 작업(지능형 Merge, 긴 맥락 추론 등)에만 사용.
3. **사용자 경험 (UX)**
    - **SSE 기반 스트리밍 응답**: 실시간 타이핑 효과 (ANSWER_CHUNK).
    - **RAG 사용 알림**: “과거 기록 검색 중… (-1 Credit)” 형태로 크레딧 차감을 명시적으로 보여줌.
    - 브랜치/커밋/머지라는 Git 개념을 그대로 UI에 노출해서 “버전 관리되는 AI 대화” 경험 제공.

### **1.2 기술 스택 (Tech Stack)**

- **Frontend**
    - Vue 3 (Composition API)
    - Pinia (전역 상태 관리: 유저, 플랜, 현재 브랜치, SSE 상태 등)
    - TailwindCSS (빠른 UI 레이아웃 및 스타일링)
    - SSE(EventSource)로 백엔드 스트리밍 구독
- **Backend**
    - Kotlin (JDK 17)
    - Spring Boot 3.2+
    - Spring WebFlux (SSE 스트리밍 처리에 유리)
    - Spring AI (OpenAI / VertexAI / Gemini 등을 추상화)
- **Database**
    - PostgreSQL: 메인 RDB
    - pgvector: 벡터 검색용(커밋/메시지 임베딩 저장 및 검색)
    - Redis:
        - 단기 컨텍스트 캐시
        - 토큰 사용량/일별 RAG 사용량 등 카운터 캐시

### **1.3 전체 데이터 흐름 (High-level Data Flow)**

1. **사용자 입력**
    - Vue → 예시 : /api/chat/send 요청 (현재 repo/branch, 메시지 내용 포함)
2. **플랜 및 토큰 체크 (API Gateway/Filter)**
    - 사용자 플랜(Free/Standard/Master)
    - 토큰 한도, RAG 사용 가능 여부 등 확인
3. **동적 컨텍스트 조립**
    - 2장에서 설명하는 알고리즘에 따라:
        - System Prompt
        - 최근 대화(Recent History)
        - 현재 브랜치 HEAD 커밋의 Long Summary
        - 조상 Lineage 커밋 정보
    - 를 합쳐 하나의 Prompt로 구성
4. **LLM Router**
    - 필요 모델/기능 결정
        - 무료플랜 유저 → Flash로 일반 대화 답변
        - 프로, 마스터 플랜 유저 → ai 프리미엄 모델로 일반 대화 답변
        - Merge, Deep 분석 → Pro, 마스터 플랜만 사용 가능
    - RAG 필요 여부는 Tool(Function calling)로 판단
5. **RAG Interceptor**
    - 모델이 search_rag_history 함수 호출 시:
        - 사용자 RAG 크레딧 확인/차감
        - pgvector로 검색 수행
        - SSE로 RAG 사용 알림 전송
        - 결과를 Prompt에 추가하고 재호출
6. **응답 스트리밍**
    - ANSWER_CHUNK 타입으로 SSE 스트림 전송
    - 프론트에서 실시간 타이핑 UI 렌더링
7. **저장**
    - 원본 메시지 → Messages 테이블 저장
    - (커밋 시점에) 요약본 생성 후 Commits 에 저장 및 벡터 인덱싱

## **2. 동적 컨텍스트 조립 (Dynamic Context Construction)**

### **2.1 토큰 예산 할당 (Token Budgeting)**

사용자 플랜별 **총 Context Window**를 100%로 보고, 영역별 비율로 나누어 사용.

| **우선순위** | **영역 (Zone)** | **비율** | **내용** |
| --- | --- | --- | --- |
| 0순위 | System & Query | ~10% | 시스템 프롬프트(페르소나/규칙) + 현재 사용자 질문 (필수, 생략 불가)  |
| 1순위 | Recent History | ~50% | 아직 커밋되지 않은 **최근 대화 원문** (20~40 턴 내) |
| 2순위 | Head Context | ~25% | **현재 브랜치 HEAD 커밋의 Long Summary, 25%를 모두 채우지 않았다면 이어져있는 조상 브랜치의 LongSummury를 가져와서 요약** |
| 3순위 | Lineage History | ~15% | HEAD 조상 커밋들의 흐름 (Short Summury → 부족 시 KeyPoint 식으로 압축) |

### **2.2 Adaptive Lineage Backfill 알고리즘**

- **목적:** Lineage History 영역을 “조상 커밋 흐름”으로 채우는 전략.
1. **조상 커밋 역순 순회**
    - 현재 HEAD 커밋의 parent부터 시작해 과거로 거슬러 올라감.
    - 각 커밋에 대해 남은 예산 확인 후 삽입 형식 결정.
2. **삽입 규칙**
    - 예산이 넉넉할 때 (예: Lineage_Budget > 70% 남음)
        - Short_Summary (3~4 문장) 기준으로 삽입
    - 예산이 30% 미만 남았을 때
        - Key_point (제목/핵심 키워드)만 삽입, 최대한 많은 커밋을 얇게 포함
    - 예산 소진 또는 Root 커밋 도달 시 중단
3. **조상 커밋 역순 순회**
    - 현재 HEAD 커밋의 parent부터 시작해 과거로 거슬러 올라감.
    - 각 커밋에 대해 남은 예산 확인 후 삽입 형식 결정.
4. **삽입 규칙**
    - 예산이 넉넉할 때 (예: Lineage_Budget > 70% 남음)
        - Short_Summary (3~4 문장) 기준으로 삽입
    - 예산이 30% 미만 남았을 때
        - Key_point (제목/핵심 키워드)만 삽입, 최대한 많은 커밋을 얇게 포함
    - 예산 소진 또는 Root 커밋 도달 시 중단

## **3. 데이터베이스 모델 (PostgreSQL + pgvector)**

### **3.1 주요 테이블 설계**

| **테이블** | **주요 컬럼** | **설명** |
| --- | --- | --- |
| users | id, email, plan_type, rag_credits | 플랜 정보 및 남은 RAG 크레딧 |
| repositories (workspace) | id, user_id, name, default_branch_id | 유저별 Workspace(프로젝트 단위) |
| branches | id, workspace_id, name, head_commit_id | Workspace 내 Branch |
| commits | id, branch_id, parent_id, key_point, short_summary, long_summary, embedding (vector) | Git의 Commit 개념. 요약/임베딩 포함 |
| messages | id, branch_id, commit_id (nullable), role, content, embedding (vector) | 실제 대화 메시지(유저/assistant) |
| credit_logs | id, user_id, amount, reason, created_at | RAG 사용 차감 이력 등 |
- **벡터 인덱스**
    - commits.embedding, messages.embedding 에 pgvector 인덱스 생성
    - 검색 시 브랜치/유저/Workspace(repo) 등으로 필터링 후 코사인 유사도 등으로 정렬

---

## **4. 백엔드 로직 상세 (Spring Boot + Kotlin)**

### **4.1 AI 서비스 추상화 (Interface Pattern)**

```kotlin
interface AiService {
    fun generateResponse(prompt: ChatPrompt): Flux<AiResponse>   // SSE용 스트리밍
    fun summarizeCommit(messages: List<Message>): CommitSummary  // 커밋 요약 생성
    fun mergeSummaries(base: String, target: String): String     // 머지용 통합 요약
}

// 예시 구현들
class GeminiFlashService(...) : AiService { ... }
class GeminiProService(...) : AiService { ... }
```

- 실제 서비스에서는
    - 일반 채팅, 요약 → FlashService, 마스터, 프로 플랜의 일반채팅은 → 프리미엄 ai 모델
    - 머지, Master 채팅 → 프리미엄 ai 모델
    - 를 라우팅 레이어에서 선택해서 사용.

---

### **4.2 메시지 전송 + RAG (SSE 스트리밍) 플로우**

1. **User → Backend**
    - /api/chat/stream (SSE) 연결 후 메시지를 전송.
2. **Prompt 조립**
    - 2장에서 정의한 비율대로 System / Recent / Head / Lineage 구성.
3. **LLM Router 1차 판단**
    - 컨텍스트 내에서 답변 가능해 보임 → 바로 응답 생성.
    - 정보 부족/과거 브랜치 지식 필요 → search_rag_history function 호출.
4. **RAG Interceptor (핵심)**
    - LLM이 search_rag_history 도구를 호출하려 할 때:
        1. **크레딧 확인**: user.rag_credits > 0 ?
            - 아니면 → LLM에 “RAG 불가, 현재 정보만으로 답변”이라는 system 메시지 추가.
        2. **차감 & 로그 기록**
            - rag_credits -= 1
            - credit_logs에 "AUTO_RAG" 등 reason 기록
        3. **SSE 이벤트 발송**
            
            ```json
            { "type": "RAG_USAGE", "msg": "과거 기록 검색 중... (-1 Credit)" }
            ```
            
        4. **pgvector 검색**
            - 현재 브랜치 / 조상 커밋 범위 내에서
                
                질문과 유사한 commits 또는 messages 검색.
                
5. **최종 응답 스트리밍**
    - { type: "ANSWER_CHUNK", data: "..." } 형식으로 구간별 전송
    - 종료 시 { type: "ANSWER_DONE" } 등으로 마무리

### **4.3 커밋(Commit) 프로세스**

1. **Trigger**
    - (자동) 대화 20 턴 누적
    - (수동) 사용자가 “Commit” 버튼 클릭
2. **Input**
    - 현재 브랜치의 최근 대화 버퍼 (예: 마지막 커밋 이후 20~40 메시지)
    - 필요 시 직전 커밋의 long_summary 도 함께 제공 (문맥 보강용)
3. **LLM 호출 (Gemini Flash)**
    - 출력 JSON 포맷:
        
        ```json
        {
          "key_point": "로그인 JWT 버그 수정",
          "short_summary": "토큰 만료 시간을 1시간에서 24시간으로 변경함.",
          "long_summary": "사용자가 401 에러를 호소하여... (상세 기술 내용)..."
        }
        ```
        
4. **DB 저장**
    - commits 레코드 생성 (임베딩까지 생성해서 저장 가능)
    - 해당 분량의 messages 는 “이 커밋에 속한 메시지들”로 마킹
    - **대화 버퍼 초기화**: Recent History 비우고 새로운 턴부터 다시 쌓기 시작

## **4.4 RAG (검색 증강 생성) 프로세스**

### 1. 시스템 개요 (Overview)

본 시스템은 사용자 입력의 의도(Intent)를 벡터 기반으로 실시간 분류하여, 불필요한 LLM 호출을 차단하고 검색이 필요한 경우에만 선택적으로 리소스를 사용하는 **3-Tier Routing Architecture**를 채택한다.

- **Core Goal:** 토큰 비용 절감 및 RAG Latency 최소화
- **Key Tech:** Semantic Router (Embedding based), Hybrid Search, Small LLM (Extraction)
- 철저한 분리를 위해 RAG검색은 동일한 깃 기준으로 동일한 브랜치와 조상들만 검색, 분기된 시점이 다르게 흘러간다면 해당 대화내용을 절대 찾아보지 않는다.

### 2. 전체 데이터 흐름도 (Data Flow Architecture)

시스템은 크게 **[입력 처리] → [라우팅/판단] → [전략 실행] → [최종 생성]**의 4단계 파이프라인으로 구성된다.

### 3. 상세 알고리즘 및 로직 (Detailed Logic)

### Module 1: Semantic Router (Intent Classifier)

사용자의 입력을 받아 0.05초 이내에 3가지 경로 중 하나로 분류하는 **Gatekeeper** 역할.

- **Input:** User Query String (e.g., "아까 그 코드 보여줘")
- **Algorithm:** Dense Vector Similarity Search (Cosine Similarity)
- **Process:**
    1. User Query를 임베딩 벡터(Vq)로 변환.
    2. 사전에 정의된 `Route Intents` 벡터(Vrules)와 유사도 계산.
    3. 최고 유사도 점수(Scoremax)가 임계값(Threshold, 0.8) 이상이면 해당 Route로 진입.
    4. 임계값 미만이면 Default(General Chat)로 처리.

### Module 2: Execution Strategy (The 3 Paths)

라우팅 결과에 따라 서로 다른 실행 전략(Strategy Pattern)을 수행한다.

### 🛣️ Path A: Fast Retrieval (단순 회상)

- **Trigger:** "아까 그거", "방금 전 내용", "취소해", "다시 보여줘"
- **Logic (Zero-LLM):**
    - 이 경로는 **LLM을 전혀 사용하지 않음 (Token: 0)**.
    - 사용자의 의도는 "가장 최근 컨텍스트"를 원한다는 것으로 간주.
    - 유사도가 높은 long_summury도 같이 llm에 참조

### 🛣️ Path B: Extraction & Search (정밀 검색)

- **Trigger:** "user_id 변수 쓴 코드", "로그인 함수 수정해줘", "파이썬 파일 찾아줘"
- **Logic (Small-LLM):**
    - 단순 시간 순서가 아닌, **특정 조건**이 필요함.
    - **Action 1 (Extraction):** Small LLM(gpt-4o-mini 등)에게 Query를 던져 **JSON** 추출.
        - `Input:` "user_id 변수 쓴 코드 찾아줘"
        - `Output:` `{"keyword": "user_id", "type": "variable", "lang": "python"}`
    - **Action 2 (Hybrid Search):** 추출된 키워드로 벡터 DB 조회.
        - *Vector:* "코드 검색" 의미 매칭
        - *Filter:* `content CONTAINS 'user_id'`
    - **Action 3 :** 백터 DB에서 가져온 데이터와 컨텍스트 데이터를 다시 합쳐, llm 에게 질문

### 🛣️ Path C: Direct Generation (일반 대화)

- **Trigger:** "안녕", "고마워", "리액트 장점이 뭐야?"
- **Logic (Passthrough):**
    - 과거 기억(DB)이 필요 없음.
    - **Action:** 즉시 Main LLM으로 전달. (Context Injection 없음)

### **4.5 머지(Merge) 프로세스**

1. **사용자 요청**
    - A 브랜치를 B 브랜치로 병합
        
        → /api/merge?from=A&to=B
        
2. **플랜별 다르게 처리**
- **Free**
    - Merge 기능 없음.
- **Standard (단순 Merge / Fast-forward 수준)**
    - 원하는 커밋만 선택하여 머지 가능.
    - **Fast-forward**:
        - 충돌 개념 없이 B의 HEAD를 A의 HEAD로 갱신
    - 또는 **Squash Merge** 간단 버전:
        - A 브랜치의 주요 커밋 short_summary들을 **그대로 이어붙여**
            
            B에 “Merged from A branch” 라는 하나의 새 Commit 생성
            
- **Master (지능형 Deep Merge)**
    - Gemini Pro 사용
    - 사용자가 코멘트로 원하는 방향성을 ai에게 참조, 머지도 가능, 원하는 커밋만 선택 후 머지 가능
    - 입력:
        - A 브랜치의 대표 요약들 (선택된 커밋들의 long_summary)
        - B 브랜치의 대표 요약들
    - 프롬프트:
        - “두 브랜치의 내용을 통합하되,
            1. 중복 삭제
            2. 시간 순서/논리 흐름 정리
            3. 논리적인 충돌(서로 다른 결론/설계)을 명시적으로 정리” 요청
    - 결과:
        - **새로운 Merge Commit** 으로 B 브랜치에 저장
        - 필요 시, “A 브랜치에서는 이렇게 설계했지만, B 브랜치에서는 이렇게 변경됨” 식으로 충돌 해설까지 포함

## **5. 모델 운용 및 토큰 전략**

### **5.1 기능별 모델 배정**

| **기능** | **사용 모델** | **비고** |
| --- | --- | --- |
| 무료플랜 일반 채팅 | Gemini 1.5 Flash, GPT 4o-mini | 기본 대화, 설명, 간단 코드 등 |
| 커밋 요약 | Gemini 1.5 Flash, GPT 4o-mini | 단순 압축/요약 용도 |
| RAG 검색어 생성 | Gemini 1.5 Flash, GPT 4o-mini | “검색용 쿼리” 생성 정도 |
| 머지(Deep Merge) | 프로 플랜 : Gemini 1.5 Flash, GPT 4o-mini
마스터 플랜 : Gemini 1.5 Pro, GPT-4o | 브랜치 통합, 충돌 분석 |
| Master,Pro 플랜 일반 채팅 | Gemini 1.5 Pro / GPT-4o | 고난도 추론, 긴 맥락 처리 |

### **5.2 토큰 관리 규정**

- **입력(Input)**
    - 최근 대화(Recent History)는 **최대 20턴**까지만 원문 포함
    - 그 이전 내용은 “커밋 요약(Long Summary)”로 대체
    - Lineage는 **Short → KeyPoint** 순으로 압축해서 채워 넣기
- **출력(Output)**
    - 일반 채팅: 플랜별 한도
        - Free: 2k
        - Standard: 4k
        - Master: 6k
    - Commit 요약: 최대 1k 정도로 제한 (핵심만 요약하도록 유도)

## **6. 플랜별 정책 (Tier Specs)**

### **6.1 요약 표**

| **구분** | **Free (체험)** | **Standard (실속형)** | **Master (전문가)** |
| --- | --- | --- | --- |
| 메인 모델 | Gemini Flash | Gemini Flash | Gemini Flash + Gemini Pro / GPT-4o |
| Input 한도 | 4k (약 1만 자) | 8k | 16k |
| Output 한도 | 1k | 2k | 4k |
| RAG 검색 | 불가 | 일 50회 (자동 차감) | 무제한 (쿨타임 적용) |
| Merge 기능 | 불가 | 단순 이어붙이기 / Fast-forward | 지능형 통합 & 선택적 Merge |
- **Standard**
    - 브랜치/커밋 기능은 거의 모두 사용 가능
    - 다만 Merge가 **단순 결합 수준**(요약 이어붙이기, 깊은 충돌 처리는 X)
- **Master**
    - 지능형 Merge, 긴 문맥 처리, 고성능 모델 사용
    - RAG 무제한 대신 **쿨타임**(예: 10초당 1회) 등의 속도 제한으로 비용/부하 조절

## **1. 💬 채팅(Chat) – 1회 요청당 토큰 상한**

> 한 번 “보내기” 눌렀을 때,
> 
> 
> **LLM에 실제로 들어가는 최대 토큰량**
> 

> (System + 컨텍스트 + 유저 질문 전체 = Input)
> 

| **플랜** | **Input 상한 (최대 컨텍스트)** | **Output 상한 (답변 최대)** | **설명** |
| --- | --- | --- | --- |
| **Free** | 4,000 | 800 | 저가형 모델만 사용, 짧은 Q&A/코드용 |
| **Standard** | 8,000 | 1,500 | 프리미엄 모델 사용, 일반 개발/설계 충분 |
| **Master** | 16,000 | 2,500 | 긴 설계/머지/리포트까지 커버 |

**2. 📝 요약(Commit Summary) – 1회 요약당 토큰 상한**

> 전 플랜 **저가형 모델(Flash)** 하지만 **플랜이 올라갈수록 더 많은 컨텍스트를 넣어서 더 깊은 요약**
> 

### **2-1. 요약 Input/Output 상한 (플랜별)**

| **플랜** | **Summary Input 상한 (커밋 히스토리 컨텍스트)** | **Summary Output 상한 (short+long 합계)** | **요약 깊이 느낌** |
| --- | --- | --- | --- |
| **Free** | 4,000 | 1000 | 핵심 위주의 짧은 요약 (대략 1~2문단) |
| **Standard** | 6,000 | 3000 | 맥락 + 결정 + 이유까지 들어가는 중간 정도 요약 |
| **Master** | 8,000 | 7000 | 구조화된 요약(섹션 나눔, TODO 등)까지 가능 |

## **3. 💎 프리미엄 지갑 & 💸 저가형 토큰 – 월 한도**

> **프리미엄 모델(GPT-4, Claude, Gemini Pro 등)**은
> 

> “프리미엄 지갑(원 단위 예산)”에서 비용 차감.
> 

> **저가형 모델(Flash)**은 “cheap pool(토큰 상한)”에서 차감.
> 

### **3-1. 프리미엄 지갑 (월별 예산)**

| **플랜** | **월 구독료** | **프리미엄 예산(원)** | **프리미엄 토큰 대략치*** | **설명** |
| --- | --- | --- | --- | --- |
| Free | 0 | 0 | 0 | 항상 저가형만 사용 |
| Standard | 10,000 | **7,000** | **≈ 1.0M tokens** | 프리미엄 채팅용 지갑 |
| Master | 20,000 | **14,000** | **≈ 2.0M tokens** | 프리미엄 채팅 더 넉넉 |
- 대략: **1M tokens ≈ 7,000원** 수준으로 잡은 값.
- 지갑이 0원 되면:
    - 프리미엄 모델 선택 비활성화
    - UI에 “이번 달 프리미엄 사용량 소진 → 저가형으로 전환” 알림 표시

### **3-2. 저가형(Flash) cheap pool – 월 토큰 상한**

> 저가형은 싸지만
> 
> 
> **완전 무제한은 아니고** “엄청 과하게 써도 회사가 안 터지는” 정도로 상한 설정.
> 

| **플랜** | **cheap pool 총 토큰/월** | **그중 요약 전용** | **나머지(cheap 채팅/RAG 등)** | **대략 비용** |
| --- | --- | --- | --- | --- |
| **Free** | 1.5M | 0.5M | 1.0M | ≒ 430원 |
| **Standard** | 3.0M | 1.0M | 2.0M | ≒ 860원 |
| **Master** | 6.0M | 2.0M | 4.0M | ≒ 1,710원 |
- **요약 전용 버킷**에서 Commit/Merge Summary 비용 차감
    - Free: 월 약 **125회** 요약 가능 (0.5M / 4k)
    - Standard: 월 약 **250회** 요약 가능 (1.0M / 4k~6k)
    - Master: 월 약 **500회** 요약 가능 (2.0M / 4k~8k)
- cheap pool 나머지(채팅/RAG)는
    - 프리미엄 지갑이 떨어진 뒤에도 **기본 채팅/검색은 계속 가능**하게 해주는 역할.

## **4. “폭탄 방지”가 실제로 어떻게 동작하는지 요약**

1. **1회 요청당 상한**
    - Chat / Summary별로
        - Standard: input 8k / output 1.5k
        - Master: input 16k / output 2.5k
    - 컨텍스트 빌더가 이 상한에 맞춰 **오래된 히스토리부터 잘라서** 넣음.
        
        → 한 번의 실수로 20만 토큰 같은 요청이 나가는 일 없음.
        
2. **월 프리미엄 지갑**
    - 프리미엄 모델 호출 시마다 실제 사용 토큰 기반으로 예산 차감.
    - 0원 되면 → 프리미엄 선택 불가, 저가형으로 자동 전환.
3. **월 cheap pool**
    - 저가형 모델도 한 달에 쓸 수 있는 토큰 상한이 있지만
    - Free/Standard/Master 모두 **실질적으로는 “거의 무제한에 가까운 수준”**으로 설정.
    - 정말 비정상적으로 쓰는 경우에만 막아주는 안전망 역할.

---

## 설계 시 주의사항

### 💰 1**. SSE 스트리밍과 크레딧 차감 타이밍**

- **시나리오:** 답변을 생성하다가 에러가 나서 중간에 끊겼습니다. 크레딧을 차감해야 할까요?
- **전략:**
    - **선차감:** 요청 들어오자마자 차감 (유저 불만 가능성)
    - **후차감:** 답변 완료 후 차감 (먹튀 가능성)
    - **추천:** **선차감(Hold) -> 완료 후 확정(Commit) / 에러 시 환불(Rollback)** 트랜잭션 관리가 필요합니다. 특히 `premium_wallet`을 쓸 때는 더욱 민감합니다.

### 🔍 **2. RAG 검색 범위와 "Lost in Summary"**

- **상황:** 사용자가 100턴 전 대화에서 "내 API 키는 `abc-123`이야"라고 말했습니다.
- **문제:** 이 내용이 커밋되면서 `Short Summary`에는 "API 키 정보를 공유함" 정도로만 요약되고, 실제 키 값(`abc-123`)은 요약본에서 날아갈 수 있습니다.
- **보완:**
    - **Fact Extraction:** 커밋 요약 시, 단순 줄거리가 아니라 **"주요 엔티티(Key, ID, 이름, 날짜 등)"**를 별도 JSON 필드(`key_facts`)에 추출하여 저장하는 로직을 추가하면 RAG 정확도가 비약적으로 상승합니다.

### 🧠 3**. "지능형 Merge"의 딜레마 (Text vs Code)**

- **현실적인 문제:** 코드는 라인 단위로 충돌(Conflict)을 잡을 수 있지만, 텍스트(대화)는 **"맥락의 충돌"**입니다.
- A브랜치: "주인공은 **철수**로 하자."
- B브랜치: "주인공은 **영희**로 하자."
- **Master 플랜의 Deep Merge:** 단순히 두 요약을 합치면 "주인공은 철수이고 영희이다"라는 괴상한 결과가 나옵니다.
- **보완 제안:** Merge 프롬프트에 **"충돌 발생 시, 두 브랜치의 차이점을 명시하고 사용자에게 선택지를 주는 방식(Conflict Resolution UI)"**을 고려해야 합니다. 즉, AI가 알아서 합치기보다 **"합치기 애매한 부분은 리포트"**해주는 것이 UX상 더 안전할 수 있습니다. → 해당 기능은 프로랑 마스터플랜에선 선택할 수있도록 설계하자.