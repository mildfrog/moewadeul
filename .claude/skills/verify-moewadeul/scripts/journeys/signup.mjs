// 회원가입 → 메일 인증 → 로그인 상태 확인. features/signup-and-verify.md의 여정을 그대로 실행한다.
// 실행: node scripts/journeys/signup.mjs
import { expect } from 'playwright/test';
import { openApp, TEST_PASSWORD, uniqueEmail, verificationCode } from '../lib.mjs';

const email = uniqueEmail('signup');
const [localPart, domain] = email.split('@');
const nickname = `검증${Date.now().toString(36).slice(-6)}`;
const app = await openApp('signup-and-verify');
const { page, proof } = app;
let ok = false;

try {
  // 랜딩에서 회원가입으로 들어간다.
  await page.goto('/signup');
  await expect(page.getByRole('heading', { name: '회원가입' })).toBeVisible();

  await page.getByLabel('이메일 아이디').fill(localPart);
  await page.getByLabel('이메일 도메인').selectOption({ label: '직접 입력' });
  await page.getByLabel('직접 입력할 도메인').fill(domain);
  await page.getByLabel('비밀번호', { exact: true }).fill(TEST_PASSWORD);
  await page.getByLabel('비밀번호 확인').fill(TEST_PASSWORD);
  await page.getByLabel('닉네임').fill(nickname);
  await page.getByLabel('만 14세 이상이에요').check();
  await proof('signup-filled');

  await page.getByRole('button', { name: '가입하기' }).click();
  await expect(page).toHaveURL(/\/verify-email/);
  await expect(page.getByRole('heading', { name: '이메일 인증' })).toBeVisible();
  await proof('verify-email-page');

  // 실제 메일 경로: 앱이 SMTP로 보낸 메일을 Mailpit에서 읽는다.
  const code = await verificationCode(email);
  console.log(`mail code received for ${email}`);
  await page.getByLabel('인증 코드').fill(code);
  await page.getByRole('button', { name: '인증하기' }).click();
  await expect(page).toHaveURL(/\/map/);
  await proof('after-verify-map');

  // 부수 효과: 서버가 보는 회원 상태가 ACTIVE로 바뀌었다.
  const me = await page.evaluate(async () => (await fetch('/api/me', { credentials: 'same-origin' })).json());
  console.log(`api/me status=${me.status} nickname=${me.nickname}`);
  if (me.status !== 'ACTIVE' || me.nickname !== nickname) throw new Error(`예상과 다른 회원 상태: ${JSON.stringify(me)}`);
  ok = true;
  await app.close({ ok, email, nickname, me });
} catch (error) {
  await proof('failure').catch(() => {});
  await app.close({ ok, email, error: String(error) });
  console.error(error);
  process.exit(1);
}
