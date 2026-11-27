# 행사 선착순 신청

관리자가 공식 행사를 등록하면, 신청 시작 시각부터 회원이 `신청하기`로 자리를 잡는다. 신청하면 결제 기한(기본 15분)이 시작되고, 시연용 결제를 마치면 신청이 확정된다. 정원이 차면 다른 회원은 신청할 수 없다.

## Sub-features

- `program-create` 관리자가 `/admin/programs/new`에서 행사를 등록한다.
- `program-list` `/programs`와 관리자 목록에 행사가 보인다.
- `program-apply` 신청 기간에 `신청하기`를 누르면 남은 자리가 줄고 결제 대기 상태가 된다.
- `program-pay` `결제하기`를 누르면 `신청이 확정됐어요`가 된다.
- `program-mine` `/me/program-applications`에 `확정`으로 보인다.
- `program-full` 정원이 찬 행사는 다른 회원에게 `남은 자리가 없어요`와 `빈자리 알림 받기`를 보이고, 신청하면 서버가 409 `PROGRAM_SOLD_OUT`으로 막는다.
- `program-cancel` 확정 후 `신청 취소`(행사 시작 72시간 전까지 환불).

## How to get to it (user POV)

- 헤더 `주 메뉴`의 `행사` → `/programs` → 행사 카드 → `/programs/<id>`.
- 내 신청은 `/me/program-applications`.
- 관리자: `/admin/programs` → `link '행사 등록'` → `/admin/programs/new`. 신청자 목록은 `/admin/programs/<id>/applications`.

## Driving it with Playwright

Preconditions:

- `scripts/member.sh --admin`으로 관리자를, `scripts/member.sh`로 일반 회원을 만든다. 정원 확인(`program-full`)에는 일반 회원이 둘 필요하다.
- 숙박 행사가 아니면 본인확인이 필요 없다. `숙박 행사예요`는 체크하지 않는다.

- **한 번에 실행.** `node scripts/journeys/program.mjs`. 관리자 1명과 회원 2명을 만들고, 신청 시작을 1분 뒤로 잡은 행사로 아래 단계를 모두 돈다. 2분쯤 걸린다. 종료 코드 0, 증거 폴더 `artifacts/<RUN_ID>/program-apply/`에 관리자·회원 A·회원 B 화면과 `db-applications` 결과가 생긴다.
- **등록 양식(관리자).** `page.goto('/admin/programs/new')`. `getByLabel('제목', { exact: true })`, `getByLabel('설명', { exact: true })`, `getByLabel('장소 설명')`, `getByLabel('신청 시작')`, `getByLabel('신청 마감')`, `getByLabel('행사 시작')`, `getByLabel('행사 종료')`(한국 시간 `YYYY-MM-DDTHH:mm`), `getByLabel('정원 (명)')`, `getByLabel('참가비 (원)')`을 채운다.
- **등록하기.** `getByRole('button', { name: '등록하기' })`. URL이 `/admin/programs`가 되고 status `행사를 등록했어요.`와 `list '행사 목록'`의 `heading '<제목>'`, 상태 `신청 예정`, `남은 자리 1/1`이 보인다.
- **신청 열림 기다리기.** 신청 시작 전에 회원으로 `/programs/<id>`를 연다. `region '신청'`에 비활성 `button '신청하기'`와 `신청은 <시각>부터예요`가 보인다. 새로고침하지 않고 `expect(button).toBeEnabled({ timeout: 150_000 })`로 기다린다. 시각이 되면 화면이 스스로 다시 불러와 버튼이 켜지고 본문(`article`)이 `신청 받는 중`이 된다.
- **신청.** `getByRole('button', { name: '신청하기' })`. status `신청했어요. 결제 기한 안에 결제해 주세요.`, note `결제를 기다리고 있어요`, `남은 자리 0/1`, `button '결제하기'`와 `button '신청 취소'`.
- **결제.** `getByRole('button', { name: '결제하기' })`. status `결제를 마쳤어요. 신청이 확정됐어요.`.
- **내 신청.** `page.goto('/me/program-applications')`. `link '<제목> 확정 <시작 일시>'`.
- **정원 마감.** 다른 회원 B로 같은 상세를 연다. `region '신청'`에 `남은 자리가 없어요`, `button '빈자리 알림 받기'`, 켜진 `button '신청하기'`가 보인다. `신청하기`를 누르면 `POST /api/programs/<id>/applications`가 409 `{"code":"PROGRAM_SOLD_OUT"}`이고 화면에 `신청하지 못했어요`가 뜬다.
- **부수 효과.** `scripts/sql.sh "select program_id, member_id, status from program_application"`이 회원 A의 `CONFIRMED` 한 행만 보여 준다.

## Gotchas

- `getByLabel('설명')`은 `장소 설명`과, `getByLabel('제목')`은 다른 칸과 겹친다. `{ exact: true }`를 붙인다.
- `신청하기` 버튼은 신청 전에도 보인다(비활성). 보이는지로 열림을 판단하면 틀린다. 활성인지로 판단한다.
- `신청 받는 중`은 페이지 위 안내 띠에도 나온다. `article` 안으로 좁혀 찾는다.
- 신청 시작을 과거로 넣지 말고 1분 뒤로 넣는다. 등록 화면은 분 단위로 저장하므로 `신청 예정` 상태로 시작한다. 신청 버튼은 그 시각이 지나야 나온다.
- 정원이 차도 `신청하기` 버튼은 꺼지지 않는다(빈자리가 생길 수 있어서). 버튼이 없어지는 것을 기대하면 틀린다. 눌러서 409로 막히는지를 본다.
- 결제는 시연용이다. 실제 결제 기관에 연결하지 않는다.
- 관리자 계정으로 신청하지 않는다. 회원 화면 증명은 일반 회원으로 한다.
- 결제 기한(기본 15분)이 지나면 자리가 다시 열린다. 결제 단계 증명은 신청 직후에 이어서 한다.
