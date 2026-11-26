# AI 하네스

GitHub 이슈 하나를 Claude Code로 구현해 PR까지 올리는 도구다. 이 도구의 목적은 자동 구현이 아니라 구현 앞뒤의 통제다. 사람이 계획을 승인하기 전에는 코드를 쓰지 않고, 코드가 생긴 뒤에는 결정적 검사를 통과해야만 PR에 `ai-gate` 상태가 붙는다. 어느 단계에서든 실패하면 멈추고 알린다.

## 막는 것

| 위험 | 막는 방법 |
|---|---|
| 남이 쓴 이슈 글로 프롬프트 주입 | 이슈 작성자와 `claude` 라벨을 붙인 사람이 모두 저장소 소유자일 때만 받는다. 프롬프트에는 소유자가 쓴 본문과 댓글만 넣는다 |
| 승인 없이 구현 | 계획 댓글 뒤에 소유자의 `/approve` 댓글 또는 디스코드 ✅가 있어야 구현한다 |
| 검증 없이 PR | 결정적 게이트를 모두 통과해야 push하고 `ai-gate` 상태를 남긴다 |
| 실패를 놓침 | 실패하면 `ai:blocked` 라벨, 이슈 댓글(로그 끝부분), 디스코드 알림을 남긴다 |
| 하네스 상태가 사라져 중복 작업 | 상태를 로컬에 두지 않고 매 주기 GitHub 라벨과 댓글에서 다시 읽는다. 브랜치·PR·댓글이 이미 있으면 재사용한다 |

## 상태

작업 하나는 이슈 하나이고, 상태는 이슈 라벨이 가진다. 전이 표는 `harness.py`의 `STATES`다.

| 라벨 | 하네스가 하는 일 | 다음 |
|---|---|---|
| `claude` | 워크트리를 만들고 읽기 전용으로 계획을 쓴다. 이슈에 계획 댓글을 남기고 디스코드에 승인을 요청한다 | `ai:awaiting-approval` |
| `ai:awaiting-approval` | 소유자의 `/approve`·`/reject` 댓글이나 디스코드 ✅·❌를 기다린다 | `ai:implementing` 또는 `ai:rejected` |
| `ai:implementing` | `claude -p`가 계획대로 구현하고 커밋한다. 커밋이 없으면 실패다 | `ai:verifying` |
| `ai:verifying` | 게이트를 돌리고, 실패하면 Claude가 고치게 한다(최대 2회). 통과하면 push, PR, 검증 보고 댓글, `ai-gate` 상태 | `ai:pr-open` |
| `ai:pr-open`, `ai:blocked`, `ai:rejected` | 더 손대지 않는다. `claude` 라벨을 다시 붙이면 재시작 | |

라벨은 새 라벨을 먼저 붙이고 옛 라벨을 뗀다. 중간에 죽어도 두 라벨이 함께 남을 뿐 상태를 잃지 않고, 더 뒤 단계가 현재 상태가 된다.

`ai:blocked`나 `ai:rejected`로 끝난 이슈에 소유자가 `claude` 라벨을 다시 붙이면 다시 시작한다. 이미 승인된 계획과 커밋이 있으면 그대로 이어 쓰므로, 막힌 이슈는 원인을 고친 뒤 검증부터 다시 돈다. 계획을 새로 받으려면 새 이슈를 연다.

디스코드로 승인하면 하네스가 이슈에 "디스코드에서 소유자가 승인했다(시각)" 댓글을 남긴다. 승인 기록의 원본은 GitHub이다.

## 게이트

`gates.py`의 `GATES` 표다. 바뀐 파일(`origin/develop...HEAD`)에 따라 고른다.

| 게이트 | 조건 | 명령 |
|---|---|---|
| `backend-unit` | `backend/` 변경 | `./gradlew test spotlessCheck` |
| `backend-integration` | `backend/src/main/` 변경 | `./gradlew integrationTest --tests 'com.moewadeul.<바뀐 모듈>.*'`. `api` 패키지가 바뀌면 `EndpointAuthorizationCoverageIntegrationTest`를 더한다. 모듈 밖 변경(마이그레이션, 설정)이면 통합 테스트 전체 |
| `frontend` | `frontend/` 변경 | `npm ci`, `build`, `lint`, `test`, `format:check` |
| `secret-scan` | 항상 | CI와 같은 digest의 gitleaks, `origin/develop..HEAD` 범위 |

게이트가 실패하면 로그 끝 200줄을 Claude에게 주고 고치게 한 뒤 처음 게이트부터 다시 돈다. 두 번 고쳐도 실패하면 `ai:blocked`로 멈춘다.

결정적 게이트를 통과하면 AI 보안 리뷰를 읽기 전용으로 돌려 결과를 PR 보고 댓글에 싣는다. 이 결과는 참고이고 머지를 막지 않는다. 오탐과 누락이 있어서, 막는 일은 결정적 검사가 맡는다. 리뷰 출력이 JSON이 아니면 그 사실을 보고에 남긴다.

## 실행

필요한 것은 Python 3, `git`, `gh`(로그인 완료), `claude`, `docker`다. 외부 Python 패키지는 쓰지 않는다.

```
python3 tools/ai-harness/harness.py run              # 30초마다 돈다
python3 tools/ai-harness/harness.py run --once       # 한 번만 돈다
python3 tools/ai-harness/harness.py run --interval 60
```

이슈마다 `../<저장소 이름>-ai/issue-<번호>`에 워크트리를, `claude/issue-<번호>` 브랜치를 만든다. 언제 끄고 다시 켜도 라벨을 읽어 같은 지점에서 이어간다. 승인 기다리는 동안 하네스를 꺼 둬도 된다.

테스트는 가짜 `gh`·`claude`·`docker`와 로컬 bare 저장소로 돈다. 실제 GitHub, Claude, 디스코드를 부르지 않는다.

```
python3 -m unittest discover -s tools/ai-harness/tests
```

## 디스코드 준비

디스코드 설정은 선택이다. 없으면 알림 없이 이슈 댓글(`/approve`, `/reject`)로만 승인한다.

1. 디스코드 개발자 포털에서 봇을 만들고 토큰을 받는다. 서버에 초대할 때 채널 보기, 메시지 보내기, 반응 추가, 메시지 기록 보기 권한을 준다.
2. 채널 ID와 소유자 본인의 사용자 ID를 복사한다(개발자 모드 필요).
3. `tools/ai-harness/.env.example`을 `.env`로 복사해 `DISCORD_BOT_TOKEN`, `DISCORD_CHANNEL_ID`, `DISCORD_OWNER_ID`를 채운다. `.env`는 `.gitignore`에 걸려 커밋되지 않는다.

하네스는 소유자 ID가 단 ✅·❌만 승인·반려로 읽는다. 다른 사람이나 봇 자신의 반응은 무시한다. 두 반응이 함께 있으면 반려가 이긴다.

## 보안 결정

- 소유자의 글만 입력으로 받는다. 공개 저장소에서 이슈 본문과 댓글은 누구나 쓸 수 있어서 그대로 프롬프트에 넣으면 주입 통로가 된다. 라벨을 붙인 사람은 이슈 타임라인의 `labeled` 이벤트로 확인한다.
- 하네스는 바깥으로만 연결한다. 디스코드는 봇이 메시지를 보내고 반응을 읽는 REST 호출이고, 받는 웹훅이나 열린 포트가 없다.
- Claude에게 `git push`와 `gh`는 허용하지 않는다. push, PR 생성, 상태 기록은 하네스가 게이트를 통과한 뒤에만 한다.
- `ai-gate`는 필수 검사다. 사람이 연 PR은 `.github/workflows/ai-gate-required.yml`이 통과로 표시하고, `claude/` 브랜치 PR은 하네스가 남긴 상태만 인정한다.
- AI 보안 리뷰는 참고다. 막는 일은 결정적 검사에 맡긴다.

## 알려진 한계

- 재수정 횟수는 프로세스 메모리에 둔다. 검증 도중 하네스가 죽었다 다시 켜지면 횟수를 처음부터 센다.
- `claude` 라벨 검사에서 라벨을 붙인 사람은 최초 접수 때만 확인한다. 이후 단계는 이슈 작성자가 소유자인지만 확인한다.
