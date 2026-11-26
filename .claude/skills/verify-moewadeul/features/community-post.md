# 커뮤니티 글

로그인한 회원이 제목과 내용(사진은 선택)으로 글을 올리면 `/community/<id>` 상세로 이동하고, 목록 `/community`의 맨 위에 그 글이 보인다. 상세에서 좋아요와 댓글을 남길 수 있다.

## Sub-features

- `post-create` 글쓰기 양식으로 글을 올리면 상세로 이동한다.
- `post-detail` 상세에 제목(h1), 본문, 글쓴이, `수정`·`삭제` 버튼(내 글일 때)이 보인다.
- `post-list` 목록에 새 글이 링크로 보인다.
- `post-search` `글 검색` 양식으로 제목·내용·글쓴이를 찾는다.
- `post-comment` 상세의 `댓글` 양식으로 댓글을 남긴다.
- `post-spot` 장소 상세의 `이 장소로 글쓰기`로 들어오면 장소가 연결된다.

## How to get to it (user POV)

- 헤더 `주 메뉴`의 `커뮤니티` 링크 → `/community`.
- 글쓰기 화면은 주소 `/community/new`, 또는 장소 상세의 `이 장소로 글쓰기`(`/community/new?spotId=<id>`).
- 장소 상세의 `이 장소 글 보기`(`/community?spotId=<id>`).

## Driving it with Playwright

Preconditions:

- `scripts/member.sh`로 만든 회원으로 로그인했다.
- 장소 연결(`post-spot`)을 볼 때는 [공공데이터 적재](./publicdata-and-spot.md)로 장소를 먼저 넣는다.

- **한 번에 실행.** `node scripts/journeys/community.mjs`. 회원을 만들어 로그인하고 아래 단계를 모두 돈다. 종료 코드 0, 증거 폴더 `artifacts/<RUN_ID>/community-post/`에 단계별 스크린샷과 `db-post`, `db-comment` 결과가 생긴다.
- **글쓰기 화면.** `page.goto('/community/new')`. `form '글쓰기'` 안에 `textbox '제목'`, `textbox '내용'`, `button '사진 추가 0/5'`, `button '올리기'`가 있다.
- **올리기.** `getByLabel('제목').fill(...)`, `getByLabel('내용').fill(...)`, `getByRole('button', { name: '올리기' }).click()`. URL이 `/community/<숫자>`가 되고 `getByRole('heading', { level: 1 })`이 제목이다.
- **상세 확인.** `main`의 `article`에 본문 문단, 글쓴이 링크(`/members/<id>`), `link '수정'`, `button '삭제'`, `button '좋아요 0'`이 있다.
- **목록.** `page.goto('/community')`. `list` 안에 `link`의 이름이 `<제목> <본문> <닉네임> 방금 ...`으로 시작하는 항목이 있다.
- **검색.** `getByRole('searchbox', { name: '검색어' }).fill(<제목 일부>)`, `getByRole('button', { name: '검색' })`. 목록에 그 글만 남는다.
- **댓글.** 상세에서 `getByRole('textbox', { name: '댓글' }).fill(...)`, `getByRole('button', { name: '댓글 남기기' })`. `region '댓글 1'`이 된다.
- **부수 효과.** `scripts/sql.sh "select id,title,status from community_post order by id desc limit 1"`이 새 글을 `ACTIVE`로 보여 준다.

## Gotchas

- `main` 안에는 옆 안내(`complementary`)의 목록도 들어 있다. 글 개수를 셀 때 `main`의 `listitem`을 세면 안내 항목까지 세어진다. 글 링크(`a[href^="/community/"]`)만 센다.
- 글이 하나도 없으면 목록에 `아직 올라온 글이 없어요`가 뜬다. DB가 매번 비어 있으므로 이 상태가 기본이다.
- 사진 저장소는 `IMAGE_STORAGE=fake`라 실제로 업로드하지 않는다. 사진이 붙는지는 확인할 수 있지만 S3 저장은 검증되지 않는다.
- 상세 화면의 제목 h1은 `main` 밖 페이지 머리에 있다. `main`의 ARIA 스냅샷에는 제목이 나오지 않는다.
- 관리자로 로그인하면 상세에 `관리자 숨기기` 버튼이 더 보인다. 일반 회원 화면을 증명할 때는 일반 회원을 쓴다.
