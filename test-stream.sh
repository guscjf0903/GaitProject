#!/bin/bash
TOKEN=$(grep gait_access_token ~/.bash_history | tail -n 1 | awk -F'"' '{print $4}' || echo "")
if [ -z "$TOKEN" ]; then
  # 임의의 로그인 API 호출하여 토큰 획득
  TOKEN=$(curl -s -X POST "http://localhost:8080/api/auth/login" -H "Content-Type: application/json" -d '{"email":"test@test.com","password":"test"}' | grep -o '"accessToken":"[^"]*' | cut -d'"' -f4)
fi
WORKSPACE_ID=$(PGPASSWORD=postgres psql -h localhost -U postgres -d gaitdb -t -c "SELECT id FROM workspaces LIMIT 1;" | tr -d ' ')
BRANCH_ID=$(PGPASSWORD=postgres psql -h localhost -U postgres -d gaitdb -t -c "SELECT id FROM branches WHERE workspace_id = '$WORKSPACE_ID' LIMIT 1;" | tr -d ' ')
curl -s -X POST "http://localhost:8080/api/chat/stream" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"workspaceId":"'$WORKSPACE_ID'", "branchId":"'$BRANCH_ID'", "content":"안녕"}' > /dev/null
