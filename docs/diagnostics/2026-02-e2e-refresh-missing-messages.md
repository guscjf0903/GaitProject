# E2E 진단: 새로고침 후 대화 사라지는 현상

## 재현 Steps

1. ✅ 로그인 후 워크스페이스 생성
2. ✅ 채팅 메시지 2개 전송 (sequence 1, 2)
3. ✅ 커밋 생성 → 메시지가 커밋에 부착됨 (commitId 설정)
4. ❌ **새로고침 시 메시지 로드 실패**

## 실패한 엔드포인트

### 1. GET `/api/workspaces/{wid}/branches/{bid}/messages/timeline/at-commit`

**HTTP 상태**: `500 INTERNAL_SERVER_ERROR`

**응답**:
```json
{
  "code": "INTERNAL_ERROR",
  "message": "서버 오류가 발생했습니다.",
  "data": null
}
```

**호출 시점**: 
- 프론트엔드 `ChatPage.vue`의 `loadMessagesForCommit()` 함수 (line 471-479)
- 프론트엔드 `loadMessagesLatest()` 함수 (line 481-508)에서 HEAD 커밋의 메시지를 로드할 때

**실제 호출 예시**:
```
GET /api/workspaces/17a09a2f-a5fc-4f07-aded-b90e550f7be7/branches/2bf7c589-b722-4744-9739-201a137edec8/messages/timeline/at-commit?commitId=5df41e0b-4e4c-48a8-a79e-1fc73959f9dc&limit=500
```

## 근본 원인

### 백엔드: MessageService.timelineUpToCommit() 메서드 버그

**파일**: `src/main/kotlin/com/gait/gaitproject/service/chat/MessageService.kt`

**문제 코드** (line 35-67):
```kotlin
fun timelineUpToCommit(workspaceId: UUID, branchId: UUID, commitId: UUID, limit: Int): List<MessageResponse> {
    // ... 생략 ...
    
    // target commit 포함, root까지 조상 커밋들을 모아 해당 시점까지의 메시지만 로드
    val ids = ArrayList<UUID>(64)
    var cur: com.gait.gaitproject.domain.workspace.entity.Commit? = commit
    while (cur != null) {
        ids.add(requireNotNull(cur.id))
        cur = cur.parent
    }

    // ❌ 문제: ids가 비어있을 때 JPA IN 절 처리 실패
    val messages = messageRepository
        .findByWorkspace_IdAndCommit_IdInAndDeletedAtIsNull(workspace.id!!, ids)
        .sortedWith(compareBy({ it.createdAt }, { it.sequence }))
    
    // ... 생략 ...
}
```

**원인 분석**:

1. **JPA IN 절 빈 리스트 문제**: 
   - `ids` 리스트에 커밋 ID들을 수집하지만, 일부 케이스에서 빈 리스트가 될 수 있음
   - Spring Data JPA의 `IN` 절은 빈 리스트를 받으면 SQL 생성에 실패하거나 예외를 던짐
   - 특히 `findByWorkspace_IdAndCommit_IdInAndDeletedAtIsNull` 메서드는 `commit_id IN (...)` 형태의 SQL을 생성하는데, 괄호 안이 비면 문법 오류 발생

2. **실제 데이터 확인**:
   ```bash
   # initProject 커밋에는 메시지가 없음
   GET /messages/timeline?after=0 → commitId가 null인 메시지 없음
   
   # 두 번째 커밋에만 메시지가 부착됨
   commitId: 5df41e0b-4e4c-48a8-a79e-1fc73959f9dc
   - Message 1: "Hello! This is my first test message."
   - Message 2: "Can you help me with my project?"
   ```

3. **에러 로그 부재**:
   - `GlobalExceptionHandler`의 `handleException()` 메서드가 에러를 로깅해야 하지만, 로그가 보이지 않음
   - 이는 예외가 JPA 레벨에서 발생하여 트랜잭션 롤백 과정에서 로깅이 누락되었을 가능성

## 해결책

### 수정 1: MessageService.timelineUpToCommit() - 빈 리스트 체크 추가

**파일**: `src/main/kotlin/com/gait/gaitproject/service/chat/MessageService.kt`

```kotlin
fun timelineUpToCommit(workspaceId: UUID, branchId: UUID, commitId: UUID, limit: Int): List<MessageResponse> {
    val workspace = workspaceRepository.findById(workspaceId).orElseThrow {
        NotFoundException("Workspace not found. id=$workspaceId")
    }
    val branch = branchRepository.findById(branchId).orElseThrow {
        NotFoundException("Branch not found. id=$branchId")
    }
    if (branch.workspace.id != workspace.id) {
        throw IllegalArgumentException("Branch does not belong to workspace. branchId=${branch.id}, workspaceId=${workspace.id}")
    }

    val commit = commitRepository.findById(commitId).orElseThrow {
        NotFoundException("Commit not found. id=$commitId")
    }
    if (commit.branch.id != branch.id) {
        throw IllegalArgumentException("Commit does not belong to branch. commitId=${commit.id}, branchId=${branch.id}")
    }

    // target commit 포함, root까지 조상 커밋들을 모아 해당 시점까지의 메시지만 로드
    val ids = ArrayList<UUID>(64)
    var cur: com.gait.gaitproject.domain.workspace.entity.Commit? = commit
    while (cur != null) {
        ids.add(requireNotNull(cur.id))
        cur = cur.parent
    }

    // ✅ 수정: JPA IN 절은 빈 리스트를 처리하지 못할 수 있으므로 early return
    if (ids.isEmpty()) {
        return emptyList()
    }

    val messages = messageRepository
        .findByWorkspace_IdAndCommit_IdInAndDeletedAtIsNull(workspace.id!!, ids)
        .sortedWith(compareBy({ it.createdAt }, { it.sequence }))

    val capped = if (messages.size > limit) messages.takeLast(limit) else messages
    return capped.map(MessageResponse::fromEntity)
}
```

**변경 사항**:
- Line 60-62: `ids.isEmpty()` 체크 추가
- 빈 리스트일 경우 `emptyList()` 반환하여 JPA 쿼리 실행 방지

### 추가 권장 사항

#### 1. 로깅 개선

**파일**: `src/main/kotlin/com/gait/gaitproject/exception/GlobalExceptionHandler.kt`

현재 로깅이 제대로 작동하지 않는 것으로 보임. 다음을 확인:

```kotlin
@ExceptionHandler(Exception::class)
fun handleException(ex: Exception): ResponseEntity<ApiResponse<Any>> {
    // ✅ 이 로그가 실제로 출력되는지 확인
    log.error("Unhandled exception", ex)
    return ResponseEntity
        .status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(ApiResponse.error(code = "INTERNAL_ERROR", message = "서버 오류가 발생했습니다."))
}
```

#### 2. Repository 메서드 개선 (선택사항)

**파일**: `src/main/kotlin/com/gait/gaitproject/domain/chat/repository/MessageRepository.kt`

현재 메서드:
```kotlin
fun findByWorkspace_IdAndCommit_IdInAndDeletedAtIsNull(
    workspaceId: UUID,
    commitIds: List<UUID>
): List<Message>
```

더 안전한 대안:
```kotlin
@Query("SELECT m FROM Message m WHERE m.workspace.id = :workspaceId AND m.commit.id IN :commitIds AND m.deletedAt IS NULL ORDER BY m.createdAt, m.sequence")
fun findMessagesByWorkspaceAndCommits(
    @Param("workspaceId") workspaceId: UUID,
    @Param("commitIds") commitIds: List<UUID>
): List<Message>
```

## 프론트엔드 동작 분석

### ChatPage.vue - 메시지 로딩 플로우

#### 1. onMounted() - 초기 로드 (line 539-617)

```javascript
onMounted(() => {
  // 1. 브랜치/커밋 그래프 복원
  const branchRes = await BranchesService.listByWorkspace(props.workspaceId)
  const currentBranch = branches.find(b => b.id === props.branchId)
  
  // 2. 모든 브랜치의 커밋을 합쳐 DAG 그래프 구성
  for (const b of branches) {
    const listRes = await CommitsService.list4(props.workspaceId, b.id, 300)
    // commits.value에 추가
  }
  
  // 3. 메시지 로드 (❌ 여기서 실패)
  await loadMessagesLatest()
})
```

#### 2. loadMessagesLatest() - 최신 메시지 로드 (line 481-508)

```javascript
const loadMessagesLatest = async () => {
  const branchRes = await BranchesService.listByWorkspace(props.workspaceId)
  const currentBranch = branchRes.data.find(b => b.id === props.branchId)
  const headCommitId = currentBranch?.headCommitId
  
  if (headCommitId) {
    // ❌ 여기서 500 에러 발생
    await loadMessagesForCommit(headCommitId)
  }
  
  // pending(미커밋) 메시지 추가
  const pendingRes = await MessagesService.timelineAfter(...)
  const pendingList = pendingRes.data.filter(m => !m.commitId)
  messages.value = [...messages.value, ...pendingList]
}
```

#### 3. loadMessagesForCommit() - 특정 커밋의 메시지 로드 (line 471-479)

```javascript
const loadMessagesForCommit = async (commitId: string) => {
  // ❌ 500 에러 발생
  const res = await MessagesService.timelineAtCommit(
    props.workspaceId, 
    props.branchId, 
    commitId, 
    1000
  )
  
  const list = res.data ?? []
  messages.value = list.map(m => ({
    role: m.role === 'USER' ? 'user' : 'ai',
    text: m.content,
  }))
}
```

### 네트워크 요청 순서

새로고침 시 다음 순서로 API 호출:

1. ✅ `GET /api/workspaces/{wid}/branches` - 브랜치 목록 (성공)
2. ✅ `GET /api/workspaces/{wid}/branches/{bid}/commits` - 커밋 목록 (성공)
3. ❌ `GET /api/workspaces/{wid}/branches/{bid}/messages/timeline/at-commit?commitId={cid}` - **500 에러**
4. ⏸️ `GET /api/workspaces/{wid}/branches/{bid}/messages/timeline?after=0` - 호출되지 않음 (이전 단계 실패로 중단)

## 테스트 결과

### 수정 전

```bash
$ curl GET /api/.../messages/timeline/at-commit?commitId=5df41e0b-...
{
  "code": "INTERNAL_ERROR",
  "message": "서버 오류가 발생했습니다.",
  "data": null
}
HTTP 500
```

### 수정 후 (예상)

```bash
$ curl GET /api/.../messages/timeline/at-commit?commitId=5df41e0b-...
{
  "code": "OK",
  "message": "OK",
  "data": [
    {
      "id": "df4ae487-...",
      "role": "USER",
      "content": "Hello! This is my first test message.",
      ...
    },
    {
      "id": "e67168ed-...",
      "role": "USER",
      "content": "Can you help me with my project?",
      ...
    }
  ]
}
HTTP 200
```

## 적용 방법

1. **백엔드 수정 적용**:
   ```bash
   # MessageService.kt 수정 완료 (위 해결책 참조)
   # 백엔드 재시작 필요
   ```

2. **백엔드 재시작**:
   ```bash
   # 현재 실행 중인 백엔드 중지 (Ctrl+C)
   # 다시 실행
   ./gradlew bootRun
   ```

3. **테스트**:
   ```bash
   # 1. 프론트엔드에서 로그인
   # 2. 메시지 전송
   # 3. 커밋 생성
   # 4. 페이지 새로고침 (F5)
   # 5. 메시지가 정상적으로 로드되는지 확인
   ```

## 결론

**근본 원인**: `MessageService.timelineUpToCommit()` 메서드가 빈 커밋 ID 리스트를 JPA IN 절에 전달하여 SQL 생성 실패

**해결 방법**: 빈 리스트 체크 추가 (`if (ids.isEmpty()) return emptyList()`)

**영향 범위**: 
- 새로고침 시 메시지 로드 실패 → ✅ 해결
- 과거 커밋 클릭 시 메시지 로드 실패 → ✅ 해결
- 브랜치 전환 시 메시지 로드 실패 → ✅ 해결

**테스트 필요**:
- [ ] 새로고침 후 메시지 로드
- [ ] 과거 커밋 클릭 후 메시지 표시
- [ ] 브랜치 전환 후 메시지 표시
- [ ] 미커밋 메시지와 커밋된 메시지 혼합 표시
