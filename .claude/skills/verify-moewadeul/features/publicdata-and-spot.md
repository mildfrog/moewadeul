# 공공데이터 적재와 장소 상세

관리자가 `/admin/sync-jobs`에서 공공데이터 작업을 실행하면 장소가 DB에 들어온다. 회원은 `/spots/<id>`에서 장소 정보, 데이터 출처, 정보 기준일, 마지막 확인일을 본다. 휴양림과 공원 경계는 저장소 안의 원본 파일(`backend/data/`)을 쓰므로 키 없이 적재할 수 있다.

## Sub-features

- `sync-forest` `국립 자연휴양림` 작업을 실행하면 휴양림 47건이 들어온다.
- `sync-forest-standard` `공립·사립 휴양림` 작업.
- `sync-park-boundary` `공원 경계` 작업. 성공하면 `박지 경고 다시 확인`이 이어서 실행된다.
- `sync-gocamping` `고캠핑` 작업. `PUBLICDATA_SERVICE_KEY`가 있어야 한다.
- `spot-detail` 장소 상세에 이름, 유형, 출처, 정보 기준일, 마지막 확인일, 베이스캠프·커뮤니티·기록 링크가 보인다.
- `spot-weather` 장소 상세의 날씨. `PUBLICDATA_SERVICE_KEY`가 있어야 한다.

## How to get to it (user POV)

- 관리자: `/admin/sync-jobs`의 `region '작업별 상태'`에서 작업별 `button '실행'`.
- 회원: 지도에서 장소를 고르거나 주소 `/spots/<id>`. 지도는 카카오 키가 있어야 그려진다.

## Driving it with Playwright

Preconditions:

- `scripts/member.sh --admin`으로 만든 관리자로 로그인했다.
- 고캠핑과 날씨는 `doctor.sh`의 `info PUBLICDATA_SERVICE_KEY 있음`일 때만 시도한다.

- **한 번에 실행.** `node scripts/journeys/publicdata.mjs`. 관리자와 회원을 만들어 아래 단계를 모두 돈다. 같은 인스턴스에서 다시 돌려도 된다. 종료 코드 0, 증거 폴더 `artifacts/<RUN_ID>/publicdata-and-spot/`, 키가 없으면 마지막에 `건너뜀: spot-weather. PUBLICDATA_SERVICE_KEY 없음.`.
- **작업 화면.** `page.goto('/admin/sync-jobs')`. 각 작업이 `listitem` 안의 `heading`(`고캠핑`, `국립 자연휴양림`, `공립·사립 휴양림`, `공원 경계`, `박지 경고 다시 확인`)과 `button '실행'`으로 보인다. 처음에는 모두 `아직 실행한 적이 없어요`.
- **실행.** `getByRole('listitem').filter({ has: page.getByRole('heading', { name: '국립 자연휴양림' }) }).getByRole('button', { name: '실행' })`. `dialog '국립 자연휴양림 작업을 실행할까요?'`가 뜨면 그 안의 `button '실행'`.
- **완료 기다리기.** 실행 전에 `region '실행 기록'`에서 이 작업의 `완료` 기록 수를 세어 둔다. `button '새로고침'`을 2초 간격으로 누르며 그 수가 하나 늘 때까지 기다린다. `처리 47건 건너뜀 0건`이 보이고 `region '실행 기록'`에 `국립 자연휴양림 완료`가 생긴다.
- **장소 상세.** 회원(관리자도 가능)으로 `page.goto('/spots/1')`. `region '장소 상세'`에 `heading '유명산 자연휴양림'`(level 2), `paragraph '자연휴양림'`, `region '시설 정보'`의 `출처 산림청 국립자연휴양림관리소`, `정보 기준일 2026-03-25`, `마지막 확인 <오늘 MM-DD>`.
- **부수 효과.** `scripts/sql.sh "select type, count(*) from spot group by type"`이 `FOREST 47`.

## Gotchas

- 장소 id는 적재 순서를 따른다. 빈 DB에서 국립 자연휴양림을 먼저 적재하면 id 1이 `유명산 자연휴양림`이다. 다른 작업을 먼저 돌렸다면 `sql.sh`로 id를 찾는다.
- 키가 없으면 장소 상세의 `region '날씨'`가 `날씨 정보를 불러오지 못했어요`를, 지도 자리가 `지도를 불러오지 못했어요`를 보인다. 장소 상세 검증의 실패가 아니라 키가 없어 건너뛴 경로다.
- 고캠핑 작업 카드 위의 `고캠핑 데이터를 2일 넘게 불러오지 못했어요` 경고는 빈 DB에서 항상 뜬다.
- 같은 인스턴스에서 다시 실행하면 작업 카드가 처음부터 `완료`(이전 실행)를 보인다. 카드 글자만 보고 끝났다고 판단하면 이번 실행을 증명하지 못한다. 실행 기록의 완료 수 증가로 판단한다.
- 실행 기록의 작업 이름과 `완료`는 서로 다른 요소다. `getByText('국립 자연휴양림 완료')`는 찾지 못한다. `listitem`을 `hasText`로 두 번 거른다.
- 날씨는 상세보다 늦게 불러온다. 날씨 영역에 내용이 생길 때까지 기다린 뒤 성공·실패를 판단한다.
- 실행 확인 대화상자에도 `실행` 버튼이 있다. 대화상자 안으로 범위를 좁혀 누른다.
