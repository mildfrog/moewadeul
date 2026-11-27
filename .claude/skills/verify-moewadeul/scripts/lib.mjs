// 여정 스크립트가 함께 쓰는 Playwright 도우미. journeys/*.mjs가 import한다.
// 실행: node scripts/journeys/<name>.mjs  (scripts/ 안에서 npm install을 한 번 해 둔다)
import { execFileSync } from 'node:child_process';
import { mkdir, writeFile } from 'node:fs/promises';
import { homedir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { chromium } from 'playwright';

const SCRIPTS_DIR = dirname(fileURLToPath(import.meta.url));

export const FRONTEND_URL = 'http://localhost:15173';
export const MAILPIT_URL = 'http://127.0.0.1:18025';

const VERIFY_HOME = process.env.VERIFY_HOME ?? join(homedir(), '.cache', 'moewadeul-verify');
export const RUN_ID = process.env.RUN_ID ?? new Date().toISOString().replace(/[:.]/g, '-');

// 증거는 down.sh가 지우지 않는 artifacts/<RUN_ID>/<feature>/에 남는다.
export async function openApp(feature) {
  const dir = join(VERIFY_HOME, 'artifacts', RUN_ID, feature);
  await mkdir(dir, { recursive: true });
  const browser = await chromium.launch();
  const context = await browser.newContext({ baseURL: FRONTEND_URL, locale: 'ko-KR', viewport: { width: 1280, height: 900 } });
  const page = await context.newPage();
  const consoleErrors = [];
  page.on('console', (msg) => msg.type() === 'error' && consoleErrors.push(msg.text()));
  let step = 0;
  const log = [];
  const sideEffects = [];
  const pages = [page];

  // 한 단계의 증거: 스크린샷, ARIA 스냅샷, URL. 사용자 동작 직후의 상태를 남긴다. 다른 사용자 창은 두 번째 인자로 넘긴다.
  async function proof(name, target = page) {
    step += 1;
    const base = join(dir, `${String(step).padStart(2, '0')}-${name}`);
    await target.screenshot({ path: `${base}.png`, fullPage: true });
    await writeFile(`${base}.aria.txt`, await target.locator('body').ariaSnapshot());
    log.push({ step, name, url: target.url() });
    console.log(`proof ${step} ${name} ${target.url()}`);
  }

  // 화면 밖 부수 효과(DB 행)를 증거로 남긴다.
  async function proofSql(name, query) {
    const rows = sql(query);
    step += 1;
    await writeFile(join(dir, `${String(step).padStart(2, '0')}-${name}.sql.txt`), `${query}\n\n${rows.map((r) => r.join('\t')).join('\n')}\n`);
    sideEffects.push({ step, name, query, rows });
    console.log(`proof ${step} ${name} rows=${JSON.stringify(rows)}`);
    return rows;
  }

  // 같은 브라우저에 쿠키를 따로 쓰는 두 번째 사용자 창을 연다.
  async function newUserPage() {
    const ctx = await browser.newContext({ baseURL: FRONTEND_URL, locale: 'ko-KR', viewport: { width: 1280, height: 900 } });
    const p = await ctx.newPage();
    p.on('console', (msg) => msg.type() === 'error' && consoleErrors.push(msg.text()));
    pages.push(p);
    return p;
  }

  // 실패하면 열려 있는 모든 사용자 창의 마지막 상태를 남긴다.
  async function proofFailure() {
    for (const [i, p] of pages.entries()) await proof(i === 0 ? 'failure' : `failure-user${i + 1}`, p).catch(() => {});
  }

  async function close(extra = {}) {
    await writeFile(join(dir, 'run.json'), JSON.stringify({ feature, runId: RUN_ID, steps: log, sideEffects, consoleErrors, ...extra }, null, 2));
    await browser.close();
    console.log(`artifacts ${dir}`);
  }

  return { browser, context, page, proof, proofSql, newUserPage, proofFailure, close, dir };
}

// 여정 본문을 실행하고, 실패하면 실패 화면을 남긴 뒤 종료 코드 1로 끝낸다.
export async function runJourney(feature, body) {
  const app = await openApp(feature);
  try {
    const extra = (await body(app)) ?? {};
    await app.close({ ok: true, ...extra });
  } catch (error) {
    await app.proofFailure();
    await app.close({ ok: false, error: String(error?.stack ?? error) });
    console.error(error);
    process.exit(1);
  }
}

// scripts/member.sh로 인증을 마친 회원을 만든다. { email, password, memberId, role, nickname }
export function createMember({ admin = false, nickname } = {}) {
  const args = [...(admin ? ['--admin'] : []), ...(nickname ? [nickname] : [])];
  const out = execFileSync(join(SCRIPTS_DIR, 'member.sh'), args, { encoding: 'utf8' }).trim();
  const member = Object.fromEntries(out.split(' ').map((kv) => kv.split(/=(.*)/s).slice(0, 2)));
  member.memberId = Number(member.memberId);
  console.log(`member ${member.role} ${member.email} id=${member.memberId}`);
  return member;
}

// scripts/sql.sh로 검증 DB를 읽는다. 머리글을 뺀 행 배열을 돌려준다.
export function sql(query) {
  const out = execFileSync(join(SCRIPTS_DIR, 'sql.sh'), [query], { encoding: 'utf8' }).trim();
  return out === '' ? [] : out.split('\n').slice(1).map((line) => line.split('\t'));
}

// 로그인 화면으로 로그인한다. 이메일은 아이디·도메인(직접 입력)으로 나눠 넣는다.
export async function login(page, { email, password }) {
  const [local, domain] = email.split('@');
  await page.goto('/login');
  await page.getByLabel('이메일 아이디').fill(local);
  await page.getByLabel('이메일 도메인').selectOption({ label: '직접 입력' });
  await page.getByLabel('직접 입력할 도메인').fill(domain);
  await page.getByLabel('비밀번호').fill(password);
  await page.getByRole('button', { name: '로그인' }).click();
  await page.waitForURL((url) => !url.pathname.startsWith('/login'));
}

// datetime-local 칸에 넣을 한국 시간 문자열. 지금부터 minutes분 뒤.
export function kstInput(minutes) {
  return new Date(Date.now() + 9 * 3600e3 + minutes * 60e3).toISOString().slice(0, 16);
}

// 실행마다 겹치지 않는 짧은 꼬리표.
export function tag() {
  return Date.now().toString(36).slice(-6);
}

// Mailpit에서 받는 사람 주소로 온 가장 최근 메일의 본문을 기다려 읽는다.
export async function latestMailText(to, { subjectIncludes = '', timeoutMs = 20000 } = {}) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    const res = await fetch(`${MAILPIT_URL}/api/v1/search?query=${encodeURIComponent(`to:"${to}"`)}`);
    const { messages = [] } = await res.json();
    const hit = messages.find((m) => m.Subject.includes(subjectIncludes));
    if (hit) {
      const msg = await (await fetch(`${MAILPIT_URL}/api/v1/message/${hit.ID}`)).json();
      return { subject: msg.Subject, text: msg.Text };
    }
    await new Promise((r) => setTimeout(r, 500));
  }
  throw new Error(`메일이 오지 않았습니다: to=${to} subject~=${subjectIncludes}`);
}

export async function verificationCode(to) {
  const { text } = await latestMailText(to, { subjectIncludes: '이메일 인증 코드' });
  const match = text.match(/인증 코드:\s*(\d{6})/);
  if (!match) throw new Error(`인증 코드를 찾지 못했습니다:\n${text}`);
  return match[1];
}

// 실행마다 겹치지 않는 시험용 주소. 도메인은 직접 입력으로 넣는다.
export function uniqueEmail(prefix = 'verify') {
  return `${prefix}-${Date.now().toString(36)}@moewadeul.test`;
}

export const TEST_PASSWORD = 'Verify!2345x';
