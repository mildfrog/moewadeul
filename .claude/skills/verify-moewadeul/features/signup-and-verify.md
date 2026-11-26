# 회원가입과 이메일 인증

방문자가 이메일·비밀번호·닉네임으로 가입하면 앱이 바로 로그인시키고 `/verify-email`로 보낸다. 메일로 온 6자리 코드를 넣으면 회원 상태가 `UNVERIFIED`에서 `ACTIVE`로 바뀌고 `/map`으로 이동한다.

## Sub-features

- `signup-submit` 가입 양식을 채워 보내면 인증 화면으로 넘어간다.
- `signup-mail` 앱이 SMTP로 인증 코드 메일(`[뫼와들] 이메일 인증 코드`)을 보낸다.
- `verify-code` 코드를 넣으면 `/map`으로 가고 헤더에 닉네임과 `로그아웃`이 보인다.
- `verify-state` `/api/me`의 `status`가 `ACTIVE`다.
- `signup-errors` 이미 쓰는 이메일·닉네임, 약한 비밀번호는 해당 칸 아래에 오류가 뜬다.

## How to get to it (user POV)

- 헤더나 로그인 화면의 `회원가입` 링크, 또는 주소 `/signup`.
- 인증 전에 로그인하면 `/verify-email`로 돌려보내진다.
- 인증 화면의 코드 다시 받기 버튼.

## Driving it with Playwright

Preconditions:

- `scripts/doctor.sh`가 통과한다. Mailpit이 응답한다.
- 가입에 쓸 주소가 DB에 없다. `uniqueEmail()`로 실행마다 새 주소를 만든다.

- **한 번에 실행.** `node scripts/journeys/signup.mjs`. 종료 코드 0, 마지막 줄에 `api/me status=ACTIVE`, 증거 폴더 `artifacts/<RUN_ID>/signup-and-verify/`에 `01-signup-filled`, `02-verify-email-page`, `03-after-verify-map`과 `run.json`(`"ok": true`)이 생긴다.
- **가입 양식.** `page.goto('/signup')`. `heading '회원가입'`이 보인다. `getByLabel('이메일 아이디')`, `getByLabel('이메일 도메인').selectOption({ label: '직접 입력' })`, `getByLabel('직접 입력할 도메인')`, `getByLabel('비밀번호', { exact: true })`, `getByLabel('비밀번호 확인')`, `getByLabel('닉네임')`을 채우고 `getByLabel('만 14세 이상이에요').check()`.
- **보내기.** `getByRole('button', { name: '가입하기' })`. URL이 `/verify-email`이 되고 `heading '이메일 인증'`이 보인다.
- **메일 확인.** `verificationCode(email)`(lib.mjs)이 Mailpit에서 `인증 코드: NNNNNN`을 읽는다. 메일이 20초 안에 오지 않으면 실패다.
- **인증.** `getByLabel('인증 코드')`에 코드를 넣고 `getByRole('button', { name: '인증하기' })`. URL이 `/map`이 되고 헤더에 `link "내 정보: <닉네임>"`이 보인다.
- **상태 확인.** 페이지에서 `fetch('/api/me')`. `status`가 `ACTIVE`, `nickname`이 입력값이다. DB로도 `scripts/sql.sh "select status from member where email='<주소>'"`가 `ACTIVE`.

## Gotchas

- `getByLabel('비밀번호')`는 `비밀번호 확인`과 겹친다. `{ exact: true }`를 붙인다.
- 가입 API만으로는 세션이 생기지 않는다. 화면은 가입 직후 로그인 API를 따로 부른다. API로 회원을 만들 때도 로그인 후 인증해야 한다(`member.sh`가 그렇게 한다).
- 인증 후 `/map`에는 카카오 키가 없으면 `지도를 불러오지 못했어요` 안내가 뜬다. 가입 검증의 실패가 아니다.
- 콘솔의 `401 (Unauthorized)` 한 줄은 앱이 처음 `/api/me`로 로그인 여부를 묻는 정상 응답이다.
- 같은 이메일 주소를 다시 쓰면 `이미 가입한 이메일` 오류가 난다. 주소는 실행마다 새로 만든다.
