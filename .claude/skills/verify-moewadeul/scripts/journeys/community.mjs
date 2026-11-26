// 커뮤니티 글: 글쓰기 → 상세 → 목록 → 검색 → 댓글. features/community-post.md의 여정을 실행한다.
// 실행: node scripts/journeys/community.mjs
import { expect } from 'playwright/test';
import { createMember, login, runJourney, tag } from '../lib.mjs';

await runJourney('community-post', async ({ page, proof, proofSql }) => {
  const member = createMember();
  await login(page, member);
  const t = tag();
  const title = `검증 글 ${t}`;
  const body = `검증 본문 ${t}입니다.`;
  const comment = `검증 댓글 ${t}`;

  // post-create
  await page.goto('/community/new');
  const form = page.getByRole('form', { name: '글쓰기' });
  await expect(form).toBeVisible();
  await form.getByLabel('제목').fill(title);
  await form.getByLabel('내용').fill(body);
  await proof('form-filled');
  await form.getByRole('button', { name: '올리기' }).click();
  await expect(page).toHaveURL(/\/community\/\d+$/);
  const postId = Number(new URL(page.url()).pathname.split('/').pop());

  // post-detail
  await expect(page.getByRole('heading', { level: 1, name: title })).toBeVisible();
  const article = page.getByRole('main').getByRole('article');
  await expect(article.getByText(body)).toBeVisible();
  await expect(article.getByRole('link', { name: member.nickname })).toBeVisible();
  await expect(article.getByRole('link', { name: '수정' })).toBeVisible();
  await expect(article.getByRole('button', { name: '삭제' })).toBeVisible();
  await expect(article.getByRole('button', { name: '좋아요 0' })).toBeVisible();
  await proof('detail');

  // post-list
  await page.goto('/community');
  const listItem = page.getByRole('main').getByRole('list').getByRole('link', { name: new RegExp(`^${title} `) });
  await expect(listItem).toBeVisible();
  await expect(listItem).toHaveAttribute('href', `/community/${postId}`);
  await proof('list');

  // post-search: 제목 꼬리표로 찾으면 그 글만 남는다.
  await page.getByRole('searchbox', { name: '검색어' }).fill(t);
  await page.getByRole('button', { name: '검색' }).click();
  // main 안에는 옆 안내(complementary)의 목록도 있다. 글 링크(`/community/<id>`)만 센다.
  const postLinks = page.getByRole('main').locator('a[href^="/community/"]').filter({ hasNotText: '글쓰기' });
  await expect(postLinks.filter({ hasText: title })).toBeVisible();
  await expect(postLinks).toHaveCount(1);
  await proof('search-hit');
  await page.getByRole('searchbox', { name: '검색어' }).fill(`없는글${t}`);
  await page.getByRole('button', { name: '검색' }).click();
  await expect(page.getByRole('main').getByRole('link', { name: new RegExp(`^${title} `) })).toHaveCount(0);
  await proof('search-miss');

  // post-comment
  await page.goto(`/community/${postId}`);
  await page.getByRole('textbox', { name: '댓글' }).fill(comment);
  await page.getByRole('button', { name: '댓글 남기기' }).click();
  const comments = page.getByRole('region', { name: '댓글 1' });
  await expect(comments).toBeVisible();
  await expect(comments.getByText(comment)).toBeVisible();
  await proof('comment');

  // 부수 효과
  const post = await proofSql('db-post', `select id, title, status from community_post where id=${postId}`);
  if (post[0]?.[2] !== 'ACTIVE') throw new Error(`글이 ACTIVE가 아닙니다: ${JSON.stringify(post)}`);
  const rows = await proofSql('db-comment', `select post_id, content from community_comment where post_id=${postId}`);
  if (rows.length !== 1) throw new Error(`댓글 행이 1개가 아닙니다: ${JSON.stringify(rows)}`);
  return { postId, member: member.email };
});
