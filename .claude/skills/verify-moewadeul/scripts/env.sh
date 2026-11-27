# shellcheck shell=bash
# 검증 스크립트가 함께 쓰는 값. 다른 스크립트가 source한다. 직접 실행하지 않는다.

SKILL_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REPO_DIR="$(cd "$SKILL_DIR/../../.." && pwd)"
BACKEND_DIR="$REPO_DIR/backend"
FRONTEND_DIR="$REPO_DIR/frontend"

# 평소 개발 환경(3306, 8080, 5173)과 겹치지 않는 검증 전용 포트.
VERIFY_MYSQL_PORT=13306
VERIFY_SMTP_PORT=11025
VERIFY_MAILPIT_PORT=18025
VERIFY_BACKEND_PORT=18080
VERIFY_FRONTEND_PORT=15173

VERIFY_COMPOSE_PROJECT=moewadeul-verify
VERIFY_COMPOSE_FILE="$SKILL_DIR/scripts/compose.verify.yml"

# 실행 상태(pid, 로그)는 state/, 증거는 artifacts/<RUN_ID>/에 둔다. down.sh는 state/만 지운다.
VERIFY_HOME="${VERIFY_HOME:-$HOME/.cache/moewadeul-verify}"
VERIFY_STATE="$VERIFY_HOME/state"
VERIFY_ARTIFACTS="$VERIFY_HOME/artifacts"

BACKEND_URL="http://127.0.0.1:$VERIFY_BACKEND_PORT"
FRONTEND_URL="http://localhost:$VERIFY_FRONTEND_PORT"
MAILPIT_URL="http://127.0.0.1:$VERIFY_MAILPIT_PORT"

# 툴 셸은 .zshrc를 읽지 않아 sdkman의 java가 PATH에 없다.
if [[ -z "${JAVA_HOME:-}" && -d "$HOME/.sdkman/candidates/java/current" ]]; then
  export JAVA_HOME="$HOME/.sdkman/candidates/java/current"
fi
[[ -n "${JAVA_HOME:-}" ]] && export PATH="$JAVA_HOME/bin:$PATH"

# backend/.env의 DB 비밀번호를 compose와 bootRun이 함께 쓴다.
env_value() { grep -E "^$1=" "$BACKEND_DIR/.env" | head -1 | cut -d= -f2-; }

# 이 실행이 시작한 프로세스만 다룬다. 포트를 쓰는 다른 프로세스는 건드리지 않는다.
# 아무도 안 쓰면 lsof가 1로 끝난다. set -e·pipefail인 스크립트가 조용히 멈추지 않게 빈 결과를 성공으로 돌려준다.
port_pid() { { lsof -nP -iTCP:"$1" -sTCP:LISTEN -t 2>/dev/null || true; } | head -1; }

compose() { docker compose -p "$VERIFY_COMPOSE_PROJECT" -f "$VERIFY_COMPOSE_FILE" --env-file "$BACKEND_DIR/.env" "$@"; }

mysql_exec() {
  compose exec -T mysql mysql -uroot -p"$(env_value DB_ROOT_PASSWORD)" --default-character-set=utf8mb4 "$(env_value DB_NAME)" "$@" 2>/dev/null
}
