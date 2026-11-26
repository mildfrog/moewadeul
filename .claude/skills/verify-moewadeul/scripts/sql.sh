#!/usr/bin/env bash
# 검증 DB(:13306)에 쿼리를 보낸다. 부수 효과(행이 생겼는지, 상태가 바뀌었는지)를 화면과 따로 확인할 때 쓴다.
# 사용: scripts/sql.sh "select id, status from program_application"
set -euo pipefail
source "$(dirname "$0")/env.sh"
[[ $# -eq 1 ]] || { echo "사용: $0 \"<SQL>\"" >&2; exit 1; }
mysql_exec -e "$1"
