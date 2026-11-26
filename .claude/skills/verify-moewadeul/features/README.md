# 뫼와들 검증 지도

이 폴더는 뫼와들에서 사용자가 쓰는 기능을 실제로 조작해 증명하는 방법의 원본이다. 앱을 조작하기 전에 이 목차를 읽고, 맞는 기능 파일을 레시피로 쓴다.

## 기본 전제 조건

- `scripts/up.sh`로 띄운 검증 인스턴스만 조작한다. 프론트엔드 `http://localhost:15173`, 백엔드 `http://127.0.0.1:18080`, Mailpit `http://127.0.0.1:18025`, MySQL `127.0.0.1:13306`.
- DB는 tmpfs라 `up.sh`마다 빈 상태(마이그레이션과 일회용 이메일 도메인 시드만 있음)로 시작한다. 장소·행사·글은 없다.
- `scripts/doctor.sh`가 종료 코드 0이어야 한다. `info` 줄에서 외부 키가 있는지 확인한다.
- 회원이 필요하면 `scripts/member.sh`(일반) 또는 `scripts/member.sh --admin`(관리자)으로 만든다. 출력의 `email=`과 `password=`로 로그인한다.

## 조작 규칙

- 브라우저 조작은 Playwright로 한다. `scripts/lib.mjs`의 `openApp(<feature>)`가 브라우저, `baseURL`, 증거 폴더를 준비한다.
- 접근 가능한 이름(role + name, label)을 쓴다. CSS 선택자, 좌표, DOM 순서는 쓰지 않는다.
- 같은 이름이 둘 이상이면 `{ exact: true }`를 붙이거나 `filter({ has: heading })`로 범위를 좁힌다.
- 로그인 화면의 이메일은 `이메일 아이디` + `이메일 도메인`(`직접 입력`) + `직접 입력할 도메인` 세 칸으로 나뉜다. `@moewadeul.test`는 목록에 없어 항상 `직접 입력`을 고른다.
- 시각 입력(`datetime-local`)은 한국 시간 `YYYY-MM-DDTHH:mm`으로 채운다.
- 고정 시간 대기 대신 화면 상태(`toHaveURL`, `toBeVisible`, status 문구)를 기다린다.

## 증거와 건너뜀 보고

- 사용자 동작과 그 결과 상태를 함께 남긴다. `proof(name)`이 스크린샷, ARIA 스냅샷, URL을 남긴다.
- 화면 밖 부수 효과는 `scripts/sql.sh`로 DB 행을, Mailpit API로 보낸 메일을 확인해 기록한다.
- 증거는 `~/.cache/moewadeul-verify/artifacts/<RUN_ID>/<feature>/`에 남고 `down.sh`가 지우지 않는다.
- 키가 없어 못 가는 경로(지도, 날씨, AI 검색, 고캠핑)는 시도한 명령과 빠진 키를 적어 건너뜀으로 보고한다. 다른 경로로 검증했다고 적지 않는다.

## 기능 파일 형식

각 파일은 H1 제목과 사용자가 보는 동작을 설명하는 한 문단으로 시작하고, 다음 H2 네 개를 이 순서로 둔다.

1. `Sub-features`: 짧은 ID와 한 줄 설명.
2. `How to get to it (user POV)`: 사용자가 들어가는 모든 입구.
3. `Driving it with Playwright`: `Preconditions:`로 시작하고, 사용자 동작마다 정확한 호출과 관찰할 결과를 짝지은 굵은 제목 목록.
4. `Gotchas`: 검증을 망치거나 시간을 버리게 하는 함정.

## 기능

- [회원가입과 이메일 인증](./signup-and-verify.md): 가입, 메일 인증 코드, 인증 후 로그인 상태. 실행 스크립트 `scripts/journeys/signup.mjs`.
- [커뮤니티 글](./community-post.md): 글쓰기, 상세, 목록, 검색, 댓글. 실행 스크립트 `scripts/journeys/community.mjs`.
- [행사 선착순 신청](./program-apply.md): 관리자 행사 등록, 회원 신청·결제·확정, 내 신청 목록, 정원 마감. 실행 스크립트 `scripts/journeys/program.mjs`(2분쯤 걸림).
- [공공데이터 적재와 장소 상세](./publicdata-and-spot.md): 관리자 동기화 실행, 장소 상세의 출처·기준일. 실행 스크립트 `scripts/journeys/publicdata.mjs`.
- 키가 있어야 하는 기능(아직 지도에 없음): 지도 탐색과 묶음 표시(`VITE_KAKAO_JS_KEY`), 날씨·일몰(`PUBLICDATA_SERVICE_KEY`), AI 검색(`OPENAI_API_KEY`), 베이스캠프 동행 모집, 추억 기록, 신고·제재.
