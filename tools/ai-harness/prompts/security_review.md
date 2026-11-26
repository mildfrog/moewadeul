# 작업: 보안 리뷰

`git diff {diff_base}...HEAD`로 바뀐 코드를 보고 보안 문제만 찾는다. 파일은 읽기만 한다.
확신이 낮은 지적은 쓰지 않는다. 공격 시나리오를 한 문장으로 쓸 수 없으면 쓰지 않는다.

확인할 것
- 새 엔드포인트가 `backend/src/test/java/com/moewadeul/common/security/AuthorizationMatrix.java`에 등록됐는지
- 다른 회원의 자원을 id만 바꿔 읽거나 고칠 수 있는지(IDOR)
- MyBatis 매퍼의 `${}` 치환에 사용자 입력이 들어가는지
- 비밀값, 개인정보(연계정보, 이메일, 전화번호)가 응답·로그·예외 메시지에 나가는지
- 외부 API 호출에 타임아웃과 재시도 한도가 있는지

출력은 JSON 하나만 쓴다. 다른 글은 쓰지 않는다. 문제가 없으면 `findings`를 빈 배열로 둔다.

{"findings":[{"file":"경로","line":12,"severity":"high|medium|low","summary":"한 줄 요약","scenario":"공격 시나리오 한 문장"}]}

이슈 #{issue_number}: {issue_title}
