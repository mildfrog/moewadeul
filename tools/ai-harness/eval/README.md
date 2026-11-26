# 결함 심기 시험

하네스의 센서가 실제로 무엇을 잡는지 확인하려고, 알려진 결함을 하나씩 심은 브랜치에 하네스와 같은 게이트와 AI 보안 리뷰를 돌렸다. 결함은 공개 저장소에서 흔히 문제가 되는 보안 결함과, 이 저장소가 설계 규칙으로 정한 결함을 섞었다. 결함이 없는 변경(D0)을 대조군으로 넣어 AI 리뷰의 오탐도 함께 쟀다.

## 실행

```
DOCKER_HOST=unix://$HOME/.orbstack/run/docker.sock TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock \
  caffeinate -i python3 tools/ai-harness/eval/seed_eval.py --reviews 3
python3 tools/ai-harness/eval/seed_eval.py --only D2 D4 --reviews 1   # 일부만
```

결함마다 `origin/develop`에서 새 워크트리를 만들고 결함 하나만 커밋한다. 그다음 게이트를 첫 실패에서 멈추지 않고 모두 돌리고, AI 보안 리뷰를 따로 3회 돌린다. 끝나면 워크트리를 지운다. 결과는 `results/<시각>.json`(게이트 로그 끝부분과 AI 지적 원문)과 `results/<시각>.md`(표)에 남는다. D6의 가짜 키는 실행할 때 만들고, 결과 파일에서는 지운다.

## 결과 (2026-11-26, 기준 커밋 `19ef9c8e`, AI 리뷰 3회, 전체 34분)

| 결함 | 결정적 검사 | 잡은 테스트 | AI 리뷰 적중 |
|---|---|---|---|
| D0 대조군: 주석 문구만 바꿈 | 통과 | - | 지적 0건(3회 모두) |
| D1 새 API를 권한 표에 넣지 않음 | 잡음 | `EndpointAuthorizationCoverageIntegrationTest` | 3/3 |
| D2 MyBatis 검색어를 `${}`로 치환 | 잡음(우연) | `CommunityPostSearchApiIntegrationTest`의 와일드카드 이스케이프 테스트 | 3/3 |
| D3 글 수정·삭제의 작성자 확인 제거 | 잡음 | `CommunityPostReviseDeleteApiIntegrationTest`(남의 글은 403) 외 1건 | 3/3 |
| D4 가입 로그에 이메일 출력 | 놓침 | - | 3/3 |
| D5 후기 모듈이 회원 모듈에 의존 | 잡음 | `ArchitectureTest` 모듈 의존 규칙 | 0/3 |
| D6 API 키 하드코딩 | 잡음 | gitleaks(`secret-scan`) | 3/3 |
| D7 외부 API 응답 대기 시간 제한 제거 | 잡음 | `OpenAiSpotQueryInterpreterIntegrationTest`의 읽기 시간 초과 테스트 | 3/3 |
| D8 API 계층에 `@Transactional` | 잡음 | `ArchitectureTest` 트랜잭션 위치 규칙 | 0/3 |

## 해석

- 결정적 검사는 8개 중 7개를 잡았다. 그중 D2는 SQL 주입을 겨냥한 테스트가 아니라, 검색어의 `%`·`_`를 글자 그대로 찾는지 보는 테스트가 우연히 걸린 것이다. 같은 `${}`가 이런 테스트가 없는 쿼리에 들어가면 결정적 검사는 놓친다.
- D6은 Spotless도 실패했지만 형식 위반이었고 키를 본 검사는 아니다. 키를 잡은 것은 gitleaks다.
- AI 보안 리뷰는 보안 결함 6개(D1·D2·D3·D4·D6·D7)를 3회 모두 잡았고, 대조군에는 한 번도 지적하지 않았다. 설계 규칙 위반(D5·D8)은 한 번도 지적하지 않았다. 리뷰 지시문이 보안만 보라고 했기 때문이다.
- 결정적 검사가 놓친 D4(로그에 개인정보)와 우연히 잡은 D2(`${}` 치환)는 AI 리뷰만 안정적으로 잡았다. 그래서 이 둘을 결정적 검사로 옮기는 것을 다음 일로 삼는다. AI 리뷰를 필수 검사로 걸지 않는 이유도 같다. AI가 적중한 결함을 그대로 AI에 맡기지 않고, 재현 가능한 테스트로 바꿔 둔다.
- 한계. 결함 8개는 직접 고른 것이고 한 줄짜리라 찾기 쉬운 편이다. AI 리뷰의 3/3은 이 조건에서의 결과다. 여러 파일에 걸친 결함이나 큰 변경 속에 섞인 결함은 재지 않았다.

## 이 시험으로 고친 하네스

D2의 바뀐 파일이 매퍼 XML이라, 하네스가 어느 모듈인지 몰라 통합 테스트 전체(13분)를 돌았다. `mapper/<모듈>/` 경로로 모듈을 정하게 고쳐 커뮤니티 통합 테스트(2분)로 좁혔다. 고치면서 모듈 코드와 마이그레이션이 함께 바뀌면 마이그레이션을 무시하고 모듈 테스트만 돌던 문제와, `common`이 바뀌어도 `common` 테스트만 돌던 문제도 찾아 고쳤다.
