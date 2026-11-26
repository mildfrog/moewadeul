#!/usr/bin/env bash
# 이 검증이 띄운 것만 내린다: 기록한 pid의 백엔드·Vite, moewadeul-verify compose 프로젝트(tmpfs DB라 데이터도 사라진다).
# 증거(artifacts/)는 지우지 않는다. 프로세스 이름으로 죽이지 않는다.
# 사용: scripts/down.sh
set -uo pipefail
source "$(dirname "$0")/env.sh"

stop_pid() {
  local file="$VERIFY_STATE/$1.pid"
  [[ -f "$file" ]] || return 0
  local pid; pid=$(cat "$file")
  if kill -0 "$pid" 2>/dev/null; then
    kill "$pid" 2>/dev/null
    for _ in $(seq 1 20); do kill -0 "$pid" 2>/dev/null || break; sleep 0.5; done
    kill -0 "$pid" 2>/dev/null && kill -9 "$pid" 2>/dev/null
    echo "down: stopped $1 pid $pid"
  fi
  rm -f "$file"
}

stop_pid frontend
stop_pid npx
stop_pid backend
stop_pid gradle
compose down -v --remove-orphans >/dev/null 2>&1 && echo "down: compose $VERIFY_COMPOSE_PROJECT removed"
rm -f "$VERIFY_STATE"/*.log

for port in $VERIFY_MYSQL_PORT $VERIFY_SMTP_PORT $VERIFY_MAILPIT_PORT $VERIFY_BACKEND_PORT $VERIFY_FRONTEND_PORT; do
  pid=$(port_pid "$port"); [[ -z "$pid" ]] || echo "down: 경고 포트 $port를 아직 pid $pid가 씁니다(이 검증이 기록한 프로세스가 아님)."
done
echo "down: 증거는 남김 $VERIFY_ARTIFACTS"
