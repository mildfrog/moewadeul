# 작업: 게이트 실패 수정

작업 디렉터리는 이 이슈 전용 워크트리다. 아래 검사가 실패했다. 실패 로그를 읽고 원인을 고친 뒤 커밋한다.

- 테스트를 지우거나 느슨하게 바꿔서 통과시키지 않는다. 코드가 틀렸으면 코드를 고친다.
- 실패한 검사: {failed_gate}
- 이슈 #{issue_number}: {issue_title}
- `git push`와 브랜치 전환은 하지 않는다. `git add`와 `git commit`으로 커밋해야 끝난 것으로 본다.

<failure_log>
{failure_log}
</failure_log>
