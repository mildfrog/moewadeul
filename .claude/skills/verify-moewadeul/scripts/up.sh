#!/usr/bin/env bash
# 검증 전용 인스턴스를 띄운다: MySQL·Mailpit(compose) → 백엔드(bootRun :18080) → 프론트엔드(Vite :15173).
# 빈 DB에서 시작하고, 끝나면 전체 경로(브라우저 → Vite 프록시 → 백엔드 → DB)가 응답하는지 확인한 뒤 ready를 출력한다.
# 사용: scripts/up.sh
set -euo pipefail
source "$(dirname "$0")/env.sh"

fail() { echo "up: $*" >&2; exit 1; }

[[ -f "$BACKEND_DIR/.env" ]] || fail "backend/.env가 없습니다. backend/.env.example을 복사해 채우세요(SKILL.md의 '처음 한 번' 참고)."
command -v java >/dev/null && java -version 2>&1 | grep -q '"25' || fail "JDK 25가 필요합니다. sdk install java 25.0.5.0+8-tem"
docker info >/dev/null 2>&1 || fail "Docker가 꺼져 있습니다. open -a OrbStack"
[[ -d "$FRONTEND_DIR/node_modules" ]] || fail "frontend/node_modules가 없습니다. (cd frontend && npm ci)"
[[ -d "$SKILL_DIR/scripts/node_modules/playwright" ]] || fail "Playwright가 없습니다. (cd $SKILL_DIR/scripts && npm install && npx playwright install chromium)"

for port in $VERIFY_MYSQL_PORT $VERIFY_SMTP_PORT $VERIFY_MAILPIT_PORT $VERIFY_BACKEND_PORT $VERIFY_FRONTEND_PORT; do
  pid=$(port_pid "$port")
  [[ -z "$pid" ]] || fail "포트 $port를 이미 pid $pid가 씁니다. 이전 검증이 남았으면 scripts/down.sh, 아니면 그 프로세스를 확인하세요. 남의 인스턴스는 건드리지 않습니다."
done

mkdir -p "$VERIFY_STATE"
: > "$VERIFY_STATE/backend.log"
: > "$VERIFY_STATE/frontend.log"

echo "up: mysql, mailpit"
compose up -d --wait >"$VERIFY_STATE/compose.log" 2>&1 || { cat "$VERIFY_STATE/compose.log" >&2; fail "compose를 띄우지 못했습니다."; }

echo "up: backend :$VERIFY_BACKEND_PORT (첫 실행은 Gradle 의존성 때문에 몇 분 걸립니다)"
(
  cd "$BACKEND_DIR"
  nohup ./gradlew bootRun --console=plain --args="\
--server.port=$VERIFY_BACKEND_PORT \
--spring.datasource.url=jdbc:mysql://localhost:$VERIFY_MYSQL_PORT/$(env_value DB_NAME) \
--spring.mail.host=127.0.0.1 --spring.mail.port=$VERIFY_SMTP_PORT \
--spring.mail.username=verify@moewadeul.test --spring.mail.password=verify \
--spring.mail.properties.mail.smtp.starttls.enable=false \
--spring.mail.properties.mail.smtp.starttls.required=false" \
    > "$VERIFY_STATE/backend.log" 2>&1 &
  echo $! > "$VERIFY_STATE/gradle.pid"
)

deadline=$((SECONDS + 600))
until curl -sf "$BACKEND_URL/actuator/health" 2>/dev/null | grep -q '"status":"UP"'; do
  grep -q -E 'APPLICATION FAILED|BUILD FAILED|FAILURE:' "$VERIFY_STATE/backend.log" && { tail -40 "$VERIFY_STATE/backend.log" >&2; fail "백엔드가 뜨지 못했습니다. 로그: $VERIFY_STATE/backend.log"; }
  ((SECONDS < deadline)) || fail "백엔드가 10분 안에 뜨지 않았습니다. 로그: $VERIFY_STATE/backend.log"
  sleep 2
done
port_pid "$VERIFY_BACKEND_PORT" > "$VERIFY_STATE/backend.pid"

echo "up: frontend :$VERIFY_FRONTEND_PORT"
(
  cd "$FRONTEND_DIR"
  nohup npx vite --config "$SKILL_DIR/scripts/vite.verify.config.ts" > "$VERIFY_STATE/frontend.log" 2>&1 &
  echo $! > "$VERIFY_STATE/npx.pid"
)
deadline=$((SECONDS + 60))
until curl -sf -o /dev/null "$FRONTEND_URL/"; do
  ((SECONDS < deadline)) || fail "Vite가 뜨지 않았습니다. 로그: $VERIFY_STATE/frontend.log"
  sleep 1
done
port_pid "$VERIFY_FRONTEND_PORT" > "$VERIFY_STATE/frontend.pid"

# 쓸 수 있는 상태: 프록시를 거친 /api/me가 로그인 전 응답(401)을 준다.
code=$(curl -s -o /dev/null -w '%{http_code}' "$FRONTEND_URL/api/me")
[[ "$code" == 401 ]] || fail "프록시 경유 /api/me가 401이 아니라 $code입니다."

echo "ready frontend=$FRONTEND_URL backend=$BACKEND_URL mailpit=$MAILPIT_URL"
