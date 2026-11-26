# 뫼와들 작업 안내

뫼와들은 백패커가 야영지를 찾고 동행과 행사를 신청하는 서비스다. 백엔드는 Java 25·Spring Boot 4·MySQL 8.4(`backend/`), 프론트엔드는 React·Vite·TypeScript(`frontend/`)다. 설계 문서는 저장소 밖에 있으니 이 파일과 코드, 테스트를 근거로 판단한다.

## 모듈 경계

- 최상위 패키지가 모듈이고, 모듈 안은 `api`·`application`·`domain`·`infra`로 나눈다.
- 다른 모듈은 그 모듈의 `application` 패키지로만 부른다. 허용된 모듈 의존은 `backend/src/test/java/com/moewadeul/common/architecture/ArchitectureRules.java`의 `ALLOWED_DEPENDENCIES` 표가 정한다. 표에 없는 의존이 필요하면 코드를 고치기 전에 이슈에 적고 멈춘다.
- `admin`은 `member`에 의존하지 못한다. 회원 쪽 관리자 API는 `member/api` 아래에 둔다.
- `@Transactional`은 `application`에만 둔다. `*Applier`는 `OutsideTransaction` 가드를 둔 같은 패키지의 `*Service`만 부른다.
- `@Scheduled` 작업은 `ScheduledJobRunner`를 거친다. 현재 시각은 주입한 `Clock`으로만 얻는다.
- 이 규칙들은 ArchUnit 테스트(`ArchitectureTest`)가 CI에서 검사한다.

## 보안

- 새 API 경로는 `backend/src/test/java/com/moewadeul/common/security/AuthorizationMatrix.java`에 접근 등급과 함께 넣는다. 빠지면 `EndpointAuthorizationCoverageIntegrationTest`가 실패한다.
- 비밀값은 코드·설정·테스트·로그에 넣지 않는다. 설정은 환경 변수로 받고 예시는 `backend/.env.example`에 이름만 적는다. CI의 `secret-scan`(gitleaks)이 전체 이력을 검사한다.
- 개인정보(연계정보, 이메일, 전화번호)는 로그에 남기지 않는다.

## DB

- 스키마 변경은 `backend/src/main/resources/db/migration/`에 새 Flyway 파일로만 한다. 이미 있는 마이그레이션은 고치지 않는다. 번호는 가장 큰 번호 다음이다.
- MySQL은 외래 키가 자동으로 만든 인덱스를, 같은 열로 시작하는 새 인덱스가 생기면 스스로 지운다. 그 인덱스를 다시 `DROP INDEX`하지 않는다. AI가 이 DROP을 넣어 마이그레이션과 통합 테스트가 실패한 적이 있다(PR #696, `V58__community_comment_post_status_index.sql`).
- 인덱스나 쿼리를 바꾸면 `EXPLAIN`으로 쓰는 인덱스를 확인하는 통합 테스트를 함께 둔다.

## 테스트와 검증

- `cd backend && ./gradlew test`는 단위 테스트만 돈다(`integration`·`ai-eval` 태그 제외).
- `./gradlew integrationTest`는 Testcontainers로 MySQL을 띄우는 통합 테스트다. Docker가 필요하다.
- `./gradlew check`가 단위·통합·포맷(Spotless)·커버리지를 모두 돈다. PR은 CI의 `check`가 통과해야 머지된다. 포맷이 어긋나면 `./gradlew spotlessApply`.
- `aiSearchEval`은 실제 OpenAI API를 불러 비용이 드니 돌리지 않는다.
- 프론트엔드는 `cd frontend && npm ci && npm run build && npm run lint && npm test && npm run format:check`.
- 동시성 문제를 고칠 때는 고치기 전에 실패하는 통합 테스트를 먼저 커밋하고, 고친 뒤 통과하는 순서로 쌓는다.
- 테스트의 `@DisplayName`에는 기능·정책 ID를 앞에 붙인다(예: `[NFR-07]`).
- 화면 흐름 검증은 `.claude/skills/verify-moewadeul/`, 부하 측정은 `backend/load-test/README.md`를 따른다.

## PR

- 브랜치는 `develop`에서 만들고 PR도 `develop`으로 연다. `main`은 릴리스 브랜치로만 받는다.
- PR 본문은 `.github/pull_request_template.md`를 채운다. 테스트하지 못한 것은 "확인하지 않은 것"으로 따로 적는다.
- 주석은 코드로 드러나지 않는 이유만 남긴다.
- 화면 문구와 문서는 한국어 표준 맞춤법을 따른다.
