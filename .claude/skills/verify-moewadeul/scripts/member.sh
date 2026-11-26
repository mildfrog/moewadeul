#!/usr/bin/env bash
# 다른 여정의 전제 조건용 회원을 만든다. 화면과 같은 API(가입 → Mailpit의 인증 코드 → 인증)를 쓴다.
# --admin: 관리자 화면은 승격 API가 없어서 member.role을 DB에서 ADMIN으로 바꾼다. 검증용 준비 단계다.
# 사용: scripts/member.sh [--admin] [닉네임]
# 출력: email=<주소> password=<비밀번호> memberId=<id> role=<USER|ADMIN>
set -euo pipefail
source "$(dirname "$0")/env.sh"

admin=0
[[ "${1:-}" == "--admin" ]] && { admin=1; shift; }
suffix=$(date +%s%N | shasum | cut -c1-6)
nickname="${1:-검증$suffix}"
email="member-$suffix@moewadeul.test"
password='Verify!2345x'
jar=$(mktemp); trap 'rm -f "$jar"' EXIT

curl -s -c "$jar" -o /dev/null "$FRONTEND_URL/api/me"
xsrf() { awk '$6=="XSRF-TOKEN"{print $7}' "$jar" | tail -1; }
post() { curl -sf -b "$jar" -c "$jar" -H "X-XSRF-TOKEN: $(xsrf)" -H 'Content-Type: application/json' "$FRONTEND_URL$1" -d "$2"; }

member_id=$(post /api/members "{\"email\":\"$email\",\"password\":\"$password\",\"passwordConfirm\":\"$password\",\"nickname\":\"$nickname\",\"ageConfirmed\":true}" \
  | python3 -I -c 'import json,sys; print(json.load(sys.stdin)["memberId"])')
# 가입은 세션을 만들지 않는다. 화면(SignupPage)처럼 바로 로그인한다.
post /api/auth/login "{\"email\":\"$email\",\"password\":\"$password\"}" >/dev/null

code=""
for _ in $(seq 1 40); do
  id=$(curl -s "$MAILPIT_URL/api/v1/search?query=to:%22$email%22" | python3 -I -c 'import json,sys; m=json.load(sys.stdin)["messages"]; print(m[0]["ID"] if m else "")')
  if [[ -n "$id" ]]; then
    code=$(curl -s "$MAILPIT_URL/api/v1/message/$id" | python3 -I -c 'import json,re,sys; print(re.search(r"인증 코드:\s*(\d{6})", json.load(sys.stdin)["Text"]).group(1))')
    break
  fi
  sleep 0.5
done
[[ -n "$code" ]] || { echo "member: 인증 메일이 오지 않았습니다 ($email)" >&2; exit 1; }
post /api/me/email-verification "{\"code\":\"$code\"}" >/dev/null

role=USER
if ((admin)); then
  mysql_exec -e "update member set role='ADMIN' where id=$member_id"
  role=$(mysql_exec -N -e "select role from member where id=$member_id")
fi
echo "email=$email password=$password memberId=$member_id role=$role nickname=$nickname"
