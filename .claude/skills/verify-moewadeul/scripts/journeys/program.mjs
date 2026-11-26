// 행사 선착순 신청: 관리자 등록 → 신청 열림 → 회원 A 신청·결제·확정 → 회원 B는 자리 없음 → 내 신청 목록.
// features/program-apply.md의 여정을 실행한다. 신청 시작을 1분 뒤로 잡아서 2분쯤 걸린다.
// 실행: node scripts/journeys/program.mjs
import { expect } from 'playwright/test';
import { createMember, kstInput, login, runJourney, tag } from '../lib.mjs';

await runJourney('program-apply', async ({ page: adminPage, proof, proofSql, newUserPage }) => {
  const admin = createMember({ admin: true });
  const memberA = createMember();
  const memberB = createMember();
  const title = `검증 행사 ${tag()}`;

  // program-create (관리자)
  await login(adminPage, admin);
  await adminPage.goto('/admin/programs/new');
  const form = adminPage.getByRole('form', { name: '행사 등록' });
  await form.getByLabel('제목', { exact: true }).fill(title);
  await form.getByLabel('설명', { exact: true }).fill('검증용 행사 설명');
  await form.getByLabel('장소 설명').fill('검증 장소');
  await form.getByLabel('신청 시작').fill(kstInput(1));
  await form.getByLabel('신청 마감').fill(kstInput(60 * 24));
  await form.getByLabel('행사 시작').fill(kstInput(60 * 24 * 7));
  await form.getByLabel('행사 종료').fill(kstInput(60 * 24 * 8));
  await form.getByLabel('정원 (명)').fill('1');
  await form.getByLabel('참가비 (원)').fill('0');
  await proof('admin-form-filled', adminPage);
  await form.getByRole('button', { name: '등록하기' }).click();
  await expect(adminPage).toHaveURL(/\/admin\/programs$/);
  await expect(adminPage.getByRole('status').getByText('행사를 등록했어요.')).toBeVisible();
  const card = adminPage.getByRole('list', { name: '행사 목록' }).getByRole('listitem').filter({ has: adminPage.getByRole('heading', { name: title }) });
  await expect(card.getByText('신청 예정')).toBeVisible();
  const href = await card.getByRole('link', { name: `${title} 신청자 보기` }).getAttribute('href');
  const programId = Number(href.match(/\/admin\/programs\/(\d+)\//)[1]);
  await proof('admin-created', adminPage);

  // program-list (회원 A)
  const pageA = await newUserPage();
  await login(pageA, memberA);
  await pageA.goto('/programs');
  await expect(pageA.getByRole('main').getByText(title)).toBeVisible();
  await proof('list', pageA);

  // 신청 열림 기다리기: 신청 시작 시각이 지나면 상세에 '신청하기'가 나온다.
  await pageA.goto(`/programs/${programId}`);
  const applyButton = pageA.getByRole('region', { name: '신청' }).getByRole('button', { name: '신청하기' });
  // 신청 전에도 '신청하기'는 보이지만 비활성이고 '신청은 <시각>부터예요'가 붙는다. 보이는지가 아니라 활성인지를 기다린다.
  // 화면은 신청 시작 시각에 스스로 다시 불러온다(useApplyPhaseRefresh). 새로고침 없이 열리는 것까지 증명한다.
  const region = pageA.getByRole('region', { name: '신청' });
  if (await applyButton.isDisabled()) {
    await expect(region.getByText(/^신청은 .+부터예요$/)).toBeVisible();
    await proof('before-open', pageA);
  }
  await expect(applyButton).toBeEnabled({ timeout: 150_000 });
  // '신청 받는 중'은 페이지 위 안내 띠에도 나온다. 행사 본문(article) 안으로 좁힌다.
  await expect(pageA.getByRole('main').getByRole('article').getByText('신청 받는 중')).toBeVisible();
  await proof('open', pageA);

  // program-apply
  await applyButton.click();
  await expect(region.getByRole('status')).toHaveText('신청했어요. 결제 기한 안에 결제해 주세요.');
  await expect(region.getByText('결제를 기다리고 있어요')).toBeVisible();
  await expect(pageA.getByRole('main').getByRole('definition').filter({ hasText: '0/1' })).toBeVisible();
  await proof('applied', pageA);

  // program-pay
  await region.getByRole('button', { name: '결제하기' }).click();
  await expect(region.getByRole('status')).toHaveText('결제를 마쳤어요. 신청이 확정됐어요.');
  await proof('paid', pageA);

  // program-mine
  await pageA.goto('/me/program-applications');
  await expect(pageA.getByRole('main').getByRole('link', { name: new RegExp(`^${title} 확정`) })).toBeVisible();
  await proof('mine', pageA);

  // program-full (회원 B)
  const pageB = await newUserPage();
  await login(pageB, memberB);
  await pageB.goto(`/programs/${programId}`);
  // 자리가 차도 '신청하기'는 켜져 있다(빈자리가 생길 수 있어서). 눌러 보면 서버가 409 PROGRAM_SOLD_OUT으로 막는다.
  const regionB = pageB.getByRole('region', { name: '신청' });
  await expect(regionB.getByText('남은 자리가 없어요')).toBeVisible();
  await expect(regionB.getByRole('button', { name: '빈자리 알림 받기' })).toBeVisible();
  await proof('full-for-other-member', pageB);
  const [response] = await Promise.all([
    pageB.waitForResponse((res) => res.request().method() === 'POST' && /\/api\/programs\/\d+\/applications$/.test(new URL(res.url()).pathname)),
    regionB.getByRole('button', { name: '신청하기' }).click(),
  ]);
  const payload = await response.json().catch(() => ({}));
  if (response.status() !== 409 || payload.code !== 'PROGRAM_SOLD_OUT') {
    throw new Error(`정원이 찬 행사 신청이 막히지 않았습니다: ${response.status()} ${JSON.stringify(payload)}`);
  }
  await expect(regionB.getByText('신청하지 못했어요')).toBeVisible();
  await proof('sold-out-rejected', pageB);

  // 부수 효과
  const rows = await proofSql('db-applications', `select member_id, status from program_application where program_id=${programId}`);
  if (rows.length !== 1 || rows[0][0] !== String(memberA.memberId) || rows[0][1] !== 'CONFIRMED') {
    throw new Error(`신청 행이 예상과 다릅니다: ${JSON.stringify(rows)}`);
  }
  return { programId, admin: admin.email, memberA: memberA.email, memberB: memberB.email };
});
