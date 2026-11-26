// 공공데이터 적재와 장소 상세: 관리자가 '국립 자연휴양림' 작업 실행 → 완료 → 회원이 장소 상세에서 출처·기준일 확인.
// features/publicdata-and-spot.md의 여정을 실행한다. 키가 필요 없는 작업만 쓴다.
// 실행: node scripts/journeys/publicdata.mjs
import { expect } from 'playwright/test';
import { createMember, login, runJourney } from '../lib.mjs';

const JOB = '국립 자연휴양림';
const SPOT = '유명산 자연휴양림';

await runJourney('publicdata-and-spot', async ({ page, proof, proofSql, newUserPage }) => {
  const admin = createMember({ admin: true });
  const member = createMember();

  // sync-forest (관리자)
  await login(page, admin);
  await page.goto('/admin/sync-jobs');
  const status = page.getByRole('region', { name: '작업별 상태' });
  const card = status.getByRole('listitem').filter({ has: page.getByRole('heading', { name: JOB, exact: true }) });
  await expect(card).toBeVisible();
  // 같은 인스턴스에서 다시 돌리면 이전 완료 기록이 남아 있다. 실행 기록의 완료 수가 하나 늘어야 이번 실행이 끝난 것이다.
  // 작업 이름과 결과는 서로 다른 요소라 한 문자열로 찾지 않는다.
  const history = page.getByRole('region', { name: '실행 기록' });
  const doneRuns = history.getByRole('listitem').filter({ hasText: JOB }).filter({ hasText: '완료' });
  const failedRuns = history.getByRole('listitem').filter({ hasText: JOB }).filter({ hasText: '실패' });
  const doneBefore = await doneRuns.count();
  const failedBefore = await failedRuns.count();
  await proof('jobs-before');

  await card.getByRole('button', { name: '실행' }).click();
  const dialog = page.getByRole('dialog', { name: `${JOB} 작업을 실행할까요?` });
  await expect(dialog).toBeVisible();
  await proof('confirm-dialog');
  await dialog.getByRole('button', { name: '실행' }).click();
  await expect(dialog).toBeHidden();

  // 완료 기다리기: 새로고침을 누르며 이번 실행의 완료 기록이 생길 때까지.
  const deadline = Date.now() + 120_000;
  for (;;) {
    if ((await doneRuns.count()) > doneBefore) break;
    if ((await failedRuns.count()) > failedBefore) throw new Error(`작업이 실패했습니다: ${await card.innerText()}`);
    if (Date.now() > deadline) throw new Error(`작업이 2분 안에 끝나지 않았습니다: ${await card.innerText()}`);
    await page.waitForTimeout(2000);
    await status.getByRole('button', { name: '새로고침' }).click();
  }
  await expect(card).toContainText('완료');
  await expect(card).toContainText('처리');
  await proof('job-done');

  // 부수 효과: 휴양림 장소가 들어왔다.
  const counts = await proofSql('db-spots', "select count(*) from spot where type='FOREST'");
  if (Number(counts[0]?.[0]) < 1) throw new Error('FOREST 장소가 들어오지 않았습니다');
  const [[spotId]] = await proofSql('db-spot-id', `select id from spot where name='${SPOT}'`);

  // spot-detail (일반 회원)
  const memberPage = await newUserPage();
  await login(memberPage, member);
  await memberPage.goto(`/spots/${spotId}`);
  const detail = memberPage.getByRole('region', { name: '장소 상세' });
  await expect(detail.getByRole('heading', { name: SPOT, level: 2 })).toBeVisible();
  await expect(detail.getByText('자연휴양림', { exact: true })).toBeVisible();
  const facility = detail.getByRole('region', { name: '시설 정보' });
  await expect(facility.getByRole('definition').filter({ hasText: '산림청 국립자연휴양림관리소' })).toBeVisible();
  await expect(facility.getByRole('definition').filter({ hasText: /^\d{4}-\d{2}-\d{2}$/ })).toBeVisible();
  const today = new Date(Date.now() + 9 * 3600e3).toISOString().slice(5, 10);
  await expect(facility.getByRole('definition').filter({ hasText: today })).toBeVisible();
  await expect(detail.getByRole('link', { name: '이 장소에서 베이스캠프 열기' })).toHaveAttribute('href', `/basecamps/new?spotId=${spotId}`);
  await expect(detail.getByRole('link', { name: '이 장소로 글쓰기' })).toHaveAttribute('href', `/community/new?spotId=${spotId}`);
  // 날씨는 따로 불러온다. 제목 말고 내용이 생길 때까지 기다린 뒤 결과를 판단한다.
  const weather = detail.getByRole('region', { name: '날씨' });
  await expect.poll(async () => (await weather.innerText()).trim().replace(/^날씨\s*/, ''), { timeout: 20_000 }).not.toBe('');
  await proof('spot-detail', memberPage);

  const skipped = [];
  if (await weather.getByText('날씨 정보를 불러오지 못했어요').isVisible()) {
    skipped.push('건너뜀: spot-weather. PUBLICDATA_SERVICE_KEY 없음.');
  }
  skipped.forEach((line) => console.log(line));
  return { spotId: Number(spotId), skipped };
});
