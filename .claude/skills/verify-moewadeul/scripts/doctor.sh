#!/usr/bin/env bash
# 읽기 전용 점검: 지금 떠 있는 검증 인스턴스를 조작해도 되는지 답한다. 아무것도 바꾸지 않는다.
# 사용: scripts/doctor.sh   (모두 통과하면 종료 코드 0)
set -uo pipefail
source "$(dirname "$0")/env.sh"

bad=0
check() { if eval "$2" >/dev/null 2>&1; then echo "ok   $1"; else echo "FAIL $1"; bad=1; fi; }
owned() { [[ -f "$VERIFY_STATE/$1.pid" && "$(port_pid "$2")" == "$(cat "$VERIFY_STATE/$1.pid")" ]]; }

check "docker 실행 중" "docker info"
check "mysql 컨테이너 healthy (:$VERIFY_MYSQL_PORT)" "compose ps mysql --format '{{.Health}}' | grep -qx healthy"
check "mailpit 응답 (:$VERIFY_MAILPIT_PORT)" "curl -sf $MAILPIT_URL/api/v1/info"
check "백엔드 포트를 이 검증이 띄운 프로세스가 씀 (:$VERIFY_BACKEND_PORT)" "owned backend $VERIFY_BACKEND_PORT"
check "백엔드 health UP" "curl -sf $BACKEND_URL/actuator/health | grep -q '\"status\":\"UP\"'"
check "프론트엔드 포트를 이 검증이 띄운 프로세스가 씀 (:$VERIFY_FRONTEND_PORT)" "owned frontend $VERIFY_FRONTEND_PORT"
check "프록시 경유 /api/me가 401" "[[ \$(curl -s -o /dev/null -w '%{http_code}' $FRONTEND_URL/api/me) == 401 ]]"
check "Flyway 마이그레이션이 모두 성공" "[[ \$(mysql_exec -N -e 'select count(*) from flyway_schema_history where success=0') == 0 ]]"

echo "info git=$(git -C "$REPO_DIR" rev-parse --short HEAD)$(git -C "$REPO_DIR" diff --quiet || echo '+dirty') flyway=v$(mysql_exec -N -e 'select version from flyway_schema_history order by installed_rank desc limit 1' || echo '?') members=$(mysql_exec -N -e 'select count(*) from member' || echo '?')"
for key in VITE_KAKAO_JS_KEY PUBLICDATA_SERVICE_KEY OPENAI_API_KEY; do
  [[ -n "$(env_value $key)" ]] && echo "info $key 있음" || echo "info $key 없음 (관련 기능은 검증할 수 없음)"
done
exit $bad
