---
name: verify-moewadeul
description: 뫼와들 웹 앱(Spring Boot + MySQL + React/Vite)을 검증 전용 인스턴스로 띄우고, Playwright로 사용자처럼 브라우저를 조작해 기능이 실제로 동작하는지 증거(스크린샷, ARIA 스냅샷, DB 행, 보낸 메일)로 증명한다. 백엔드나 프론트엔드 변경이 화면에서 동작하는지 확인할 때, 가입·커뮤니티·행사 신청·공공데이터 적재 같은 사용자 흐름을 끝까지 돌려 볼 때 쓴다.
---

# 뫼와들 검증

평소 개발 환경(3306, 8080, 5173)과 겹치지 않는 검증 전용 인스턴스를 띄워 조작한다. 모든 명령은 저장소 루트에서 실행하고, 스크립트 경로는 `.claude/skills/verify-moewadeul/scripts/`(아래에서 `scripts/`)다.

| 구성 | 주소 | 비고 |
|---|---|---|
| 프론트엔드(Vite) | `http://localhost:15173` | `/api`를 백엔드 18080으로 프록시 |
| 백엔드(bootRun) | `http://127.0.0.1:18080` | `local` 프로필, `backend/.env`를 읽음 |
| MySQL 8.4 | `127.0.0.1:13306` | tmpfs. 내리면 데이터가 사라짐 |
| Mailpit | SMTP `:11025`, API `http://127.0.0.1:18025` | 앱이 보낸 메일을 받아 둠. 밖으로 보내지 않음 |

## 처음 한 번

- JDK 25: `sdk install java 25.0.5.0+8-tem`. 툴 셸은 `.zshrc`를 읽지 않으므로 `env.sh`가 `~/.sdkman/candidates/java/current`를 `JAVA_HOME`으로 잡는다.
- Docker: `open -a OrbStack`.
- `backend/.env`: `backend/.env.example`을 복사해 채운다. `DB_PASSWORD`, `DB_ROOT_PASSWORD`, `CI_HMAC_KEY`, `MAIL_SECRET_HMAC_KEY`(뒤의 둘은 `openssl rand -base64 32`)는 필수다. `VITE_KAKAO_JS_KEY`, `PUBLICDATA_SERVICE_KEY`, `OPENAI_API_KEY`가 비어 있으면 지도, 날씨·고캠핑, AI 검색을 검증할 수 없다. 메일 설정은 비워 둬도 된다(검증 때는 Mailpit으로 돌림).
- 의존성: `(cd frontend && npm ci)`, `(cd .claude/skills/verify-moewadeul/scripts && npm install && npx playwright install chromium)`. Playwright는 1.64.1로 고정했다. 이 맥에는 Chrome이 없어 Playwright Chromium을 쓴다.

## Launch

```bash
.claude/skills/verify-moewadeul/scripts/up.sh
```

- MySQL·Mailpit을 compose 프로젝트 `moewadeul-verify`로 띄우고(`--wait`로 healthy까지), 백엔드를 `./gradlew bootRun`으로 18080에 띄운 뒤 Vite를 15173에 띄운다.
- 백엔드는 빈 DB에 Flyway 마이그레이션 75개를 적용하고 일회용 이메일 도메인 9천여 개를 시드한다. 장소·행사·글·회원은 없다.
- 준비 완료는 마지막 줄 `ready frontend=... backend=... mailpit=...`이다. 이 줄은 백엔드 `/actuator/health`가 `UP`이고 Vite 프록시를 거친 `/api/me`가 401(로그인 전)을 줄 때만 나온다.
- 첫 실행은 Gradle 의존성을 받느라 몇 분 걸린다. 그 뒤로는 30초 안팎이다. 실패하면 로그 경로를 출력하고 종료 코드 1로 끝난다.
- 검증 포트를 다른 프로세스가 쓰고 있으면 시작하지 않는다. 남의 인스턴스를 건드리지 않는다. 동시에 한 인스턴스만 띄울 수 있다.

## Doctor

```bash
.claude/skills/verify-moewadeul/scripts/doctor.sh
```

읽기 전용이다. 이상해 보이면 먼저 이것을 돌린다. 확인하는 것은 Docker, MySQL healthy, Mailpit 응답, 백엔드·프론트엔드 포트를 **이 검증이 기록한 pid**가 쓰는지, health `UP`, 프록시 경유 401, Flyway 실패 행 0개다. 종료 코드 0이면 조작해도 된다. `info` 줄에 git 커밋(`+dirty`는 작업 트리 변경 있음), Flyway 버전, 회원 수, 외부 키 유무가 나온다. 작업 트리를 바꿨는데 백엔드를 다시 띄우지 않았다면 그 변경은 실행 중인 인스턴스에 없다(Vite는 프론트엔드 변경을 바로 반영하고, 백엔드는 `down.sh` → `up.sh`가 필요하다).

## Drive

브라우저는 Playwright로 조작한다. 여정 스크립트는 `scripts/lib.mjs`를 import한다.

```js
import { expect } from 'playwright/test';
import { createMember, login, runJourney } from '../lib.mjs';

await runJourney('<feature-id>', async ({ page, proof, proofSql, newUserPage }) => {
  const member = createMember();              // scripts/member.sh. { admin: true }면 관리자
  await login(page, member);                  // baseURL = http://localhost:15173
  await page.goto('/community/new');
  await page.getByLabel('제목').fill('...');
  await proof('filled');                      // 스크린샷 + ARIA 스냅샷 + URL
  await proofSql('db-post', 'select ...');    // DB 행을 증거로 남기고 행 배열을 돌려준다
  const other = await newUserPage();          // 쿠키가 따로인 두 번째 사용자 창. proof('x', other)
  return { member: member.email };            // run.json에 함께 기록된다
});                                           // 실패하면 failure 화면을 남기고 종료 코드 1
```

- 완성된 여정은 `scripts/journeys/`에 있다. 저장소 루트에서 `node .claude/skills/verify-moewadeul/scripts/journeys/<이름>.mjs`로 실행한다. 모두 같은 인스턴스에서 여러 번 돌려도 된다(실행마다 새 회원·새 제목을 쓴다).

| 여정 | 기능 | 걸리는 시간 |
|---|---|---|
| `signup.mjs` | 가입 → 메일 인증 → `ACTIVE` | 10초 안팎 |
| `community.mjs` | 글쓰기 → 상세 → 목록 → 검색(있음·없음) → 댓글 → DB 확인 | 10초 안팎 |
| `program.mjs` | 관리자 행사 등록 → 신청 열림 → 회원 A 신청·결제·확정 → 회원 B 정원 마감 → DB 확인 | 2분쯤 (신청 시작을 1분 뒤로 잡음) |
| `publicdata.mjs` | 관리자 국립 자연휴양림 적재 → 완료 → 회원 장소 상세(출처·기준일) → 날씨 건너뜀 판단 | 20초 안팎 |

- 같은 `RUN_ID`를 환경 변수로 주면 여러 여정의 증거가 한 폴더 아래 모인다: `RUN_ID=check-1 node .../community.mjs`.
- 접근 가능한 이름(role + name, label)으로 찾는다. 기능별 정확한 이름과 관찰할 결과는 `features/` 지도에 있다. **조작 전에 `features/README.md`를 읽는다.**
- 전제 조건 회원: `scripts/member.sh`(일반), `scripts/member.sh --admin`(관리자). 화면과 같은 가입 → 로그인 → 메일 인증 API를 쓰고 `email=... password=... memberId=... role=...`을 출력한다. 관리자는 승격 API가 없어 `member.role`을 DB에서 `ADMIN`으로 바꾼다. 이 변경은 다음 로그인부터 적용된다.
- 로그인: `/login`에서 `getByLabel('이메일 아이디')`, `getByLabel('이메일 도메인').selectOption({ label: '직접 입력' })`, `getByLabel('직접 입력할 도메인')`, `getByLabel('비밀번호')`, `getByRole('button', { name: '로그인' })`.
- 메일: `verificationCode(email)` 또는 `latestMailText(email, { subjectIncludes })`. Mailpit 화면 `http://127.0.0.1:18025`에서 사람이 볼 수도 있다.
- API를 직접 부를 때는 먼저 `GET /api/me`로 `XSRF-TOKEN` 쿠키를 받고, 바꾸는 요청마다 `X-XSRF-TOKEN` 헤더에 그 값을 넣는다(세션 쿠키 `SESSION`).

## Evidence

- 위치: `~/.cache/moewadeul-verify/artifacts/<RUN_ID>/<feature>/`. `RUN_ID`를 환경 변수로 주지 않으면 실행 시각이다. 단계마다 `NN-<name>.png`, `NN-<name>.aria.txt`가 생기고, 끝에 `run.json`(단계, URL, 콘솔 오류, 결과)이 생긴다. `down.sh`는 이 폴더를 지우지 않는다.
- 사용자 경로로 증명한다. 테스트 전용 엔드포인트나 내부 setter, DB 직접 수정으로 결과를 만들지 않는다. 예외는 관리자 승격처럼 사용자 경로가 아예 없는 준비 단계뿐이고, 그때는 보고서에 그렇다고 적는다.
- 동작과 결과 상태를 함께 남긴다. 최종 화면 한 장으로는 부족하다.
- 화면 밖 부수 효과를 따로 확인한다. DB는 `scripts/sql.sh "<SELECT ...>"`, 보낸 메일은 Mailpit API(`/api/v1/search?query=to:"<주소>"`).
- 목(mock)은 쓰지 않는다. 메일은 실제 SMTP 경로로 Mailpit에 보내고, 이미지 저장소는 앱 기본값인 `fake`, 본인확인과 결제는 앱에 원래 있는 시연용 공급자를 쓴다. 그래서 S3 업로드, 실제 본인확인, 실제 결제는 이 스킬로 검증되지 않는다.
- 키가 없어 못 간 경로는 `건너뜀: <경로>. <빠진 키>.`로 보고한다.

## Cleanup

```bash
.claude/skills/verify-moewadeul/scripts/down.sh
```

`up.sh`가 기록한 pid(Vite, npx, 백엔드 JVM, gradlew)만 종료하고, compose 프로젝트 `moewadeul-verify`를 볼륨과 함께 지운다(DB 내용이 사라짐). 상태 로그도 지운다. 증거 폴더는 남긴다. 프로세스 이름으로 죽이지 않는다. Gradle 데몬은 평소 개발과 같이 쓰므로 남겨 둔다. 실패한 시도 뒤에도 매번 `down.sh`를 돌려 포트를 비운다. 마지막에 아직 검증 포트를 쓰는 낯선 pid가 있으면 경고만 한다.

## Helpers

| 스크립트 | 하는 일 |
|---|---|
| `scripts/up.sh` | 검증 인스턴스 시작, `ready` 줄 출력 |
| `scripts/doctor.sh` | 읽기 전용 점검, 종료 코드 0 = 조작 가능 |
| `scripts/down.sh` | 이 검증이 띄운 것만 정리, 증거는 남김 |
| `scripts/member.sh [--admin] [닉네임]` | 인증까지 마친 회원 생성 |
| `scripts/sql.sh "<SQL>"` | 검증 DB에 쿼리 |
| `scripts/lib.mjs` | `runJourney`, `proof`, `proofSql`, `newUserPage`, `createMember`, `login`, `sql`, `kstInput`, Mailpit 읽기 |
| `scripts/journeys/*.mjs` | 기능별 여정 4개(위 표) |
| `scripts/env.sh`, `compose.verify.yml`, `vite.verify.config.ts` | 포트·경로, 검증용 MySQL·Mailpit, 검증용 Vite 설정 |

## 기능 지도

`features/README.md`가 목차다. 지도에 입구가 여럿 나와 있는데 하나만 돌렸다면, 그 검증은 미완성이다. 앱이 바뀌면 지도도 고친다.
