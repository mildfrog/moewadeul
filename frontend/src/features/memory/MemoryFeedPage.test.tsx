import { act } from 'react';
import { createRoot } from 'react-dom/client';
import type { Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '../../api/client';
import { fetchMemoryFeed } from '../../api/memories';
import type { CursorPage, MemoryFeedItem } from '../../api/types';
import { MemoryFeedPage } from './MemoryFeedPage';

vi.mock('../../api/memories', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../api/memories')>()),
  fetchMemoryFeed: vi.fn(),
}));

vi.mock('../notification/unreadCount', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../notification/unreadCount')>()),
  refreshUnreadCount: vi.fn(),
}));

vi.mock('../../app/session/session', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../app/session/session')>()),
  useSession: () => ({ status: 'authenticated', me: { memberId: 1, nickname: '산사람', status: 'ACTIVE' } }),
}));

Object.assign(globalThis, { IS_REACT_ACT_ENVIRONMENT: true });

function item(id: number, overrides: Partial<MemoryFeedItem> = {}): MemoryFeedItem {
  return {
    memoryId: id,
    author: { memberId: 7, nickname: '능선' },
    approxLat: 36.35,
    approxLng: 127.38,
    visitedDate: '2026-10-03',
    nights: 1,
    excerpt: `기록 본문${id}`,
    imageCount: 0,
    likeCount: 3,
    createdAt: '2026-10-04T00:00:00Z',
    ...overrides,
  };
}

function cursorPage(items: MemoryFeedItem[], hasNext: boolean): CursorPage<MemoryFeedItem> {
  const last = items[items.length - 1];
  return { content: items, size: 24, hasNext, nextCursor: hasNext && last ? last.memoryId : null };
}

describe('기록 피드 화면', () => {
  let container: HTMLDivElement;
  let root: Root;

  const button = (label: string) =>
    [...container.querySelectorAll('button')].find((element) => element.textContent === label);
  const cell = (id: number) => container.querySelector<HTMLAnchorElement>(`a[href="/memories/${id}"]`);

  async function open() {
    await act(async () => root.render(<MemoryFeedPage />));
  }

  beforeEach(() => {
    vi.spyOn(window, 'scrollTo').mockImplementation(() => undefined);
    vi.mocked(fetchMemoryFeed).mockReset();
    container = document.createElement('div');
    document.body.append(container);
    root = createRoot(container);
  });

  afterEach(async () => {
    await act(async () => root.unmount());
    container.remove();
    vi.restoreAllMocks();
  });

  it('불러온 기록을 칸마다 글쓴이, 방문일, 사진 수, 좋아요 수를 읽어 주는 링크로 보여 준다', async () => {
    vi.mocked(fetchMemoryFeed).mockResolvedValue(
      cursorPage([item(1, { imageCount: 2, thumbnailUrl: 'https://img.test/1.jpg' }), item(2)], false),
    );

    await open();

    expect(fetchMemoryFeed).toHaveBeenCalledWith(null, 24, expect.anything());
    expect(cell(1)?.getAttribute('aria-label')).toBe('능선님의 기록, 10월 3일 (토), 사진 2장, 좋아요 3개');
    expect(cell(1)?.querySelector('img')?.getAttribute('src')).toBe('https://img.test/1.jpg');
    expect(cell(2)?.getAttribute('aria-label')).toBe('능선님의 기록, 10월 3일 (토), 좋아요 3개');
    expect(cell(2)?.textContent).toContain('기록 본문2');
    expect(button('더 보기')).toBeUndefined();
    expect(container.querySelector('a[href="/me/memories"]')?.textContent).toBe('내 기록 지도');
  });

  it('대표 사진을 불러오지 못하면 그 칸은 기록 본문으로 바뀐다', async () => {
    vi.mocked(fetchMemoryFeed).mockResolvedValue(
      cursorPage([item(1, { thumbnailUrl: 'https://img.test/broken.jpg', imageCount: 1 })], false),
    );
    await open();
    expect(cell(1)?.textContent).not.toContain('기록 본문1');

    await act(async () => {
      cell(1)?.querySelector('img')?.dispatchEvent(new Event('error'));
    });

    expect(cell(1)?.querySelector('img')).toBeNull();
    expect(cell(1)?.textContent).toContain('기록 본문1');
  });

  it('공개된 기록이 하나도 없으면 빈 상태 안내와 내 기록 지도 링크를 보여 준다', async () => {
    vi.mocked(fetchMemoryFeed).mockResolvedValue(cursorPage([], false));

    await open();

    expect(container.textContent).toContain('공개된 기록이 아직 없어요');
    expect(container.textContent).toContain('다녀온 곳의 기록을 남기고 공개로 설정하면 이곳에 표시돼요.');
  });

  it('처음 불러오지 못하면 오류 문구를 보여 주고 다시 불러오기를 누르면 기록이 나온다', async () => {
    vi.mocked(fetchMemoryFeed)
      .mockRejectedValueOnce(
        new ApiError(500, 'INTERNAL_ERROR', { code: 'INTERNAL_ERROR', message: 'x', traceId: 'trace-4' }),
      )
      .mockResolvedValueOnce(cursorPage([item(1)], false));

    await open();

    expect(container.querySelector('[role="alert"]')?.textContent).toContain('기록을 불러오지 못했어요');
    expect(container.textContent).toContain('일시적인 오류가 발생했어요');
    expect(container.textContent).toContain('문의 번호 trace-4');
    expect(container.textContent).not.toContain('공개된 기록이 아직 없어요');

    await act(async () => button('다시 불러오기')?.click());

    expect(cell(1)).not.toBeNull();
    expect(container.textContent).not.toContain('기록을 불러오지 못했어요');
  });

  it('더 보기를 누르면 다음 쪽 기록이 뒤에 붙고 마지막 쪽이면 더 보기가 사라진다', async () => {
    vi.mocked(fetchMemoryFeed).mockImplementation(async (cursor) =>
      cursor === null ? cursorPage([item(1), item(2)], true) : cursorPage([item(3)], false),
    );
    await open();
    expect(cell(3)).toBeNull();

    await act(async () => button('더 보기')?.click());

    expect([...container.querySelectorAll('main li a')].map((link) => link.getAttribute('href'))).toEqual([
      '/memories/1',
      '/memories/2',
      '/memories/3',
    ]);
    expect(button('더 보기')).toBeUndefined();
  });

  it('더 보기는 직전 응답의 nextCursor를 cursor로 보내 이어 받고, 첫 요청에는 cursor가 없다', async () => {
    vi.mocked(fetchMemoryFeed).mockImplementation(async (cursor) => {
      if (cursor === null) return cursorPage([item(30), item(20)], true);
      if (cursor === 20) return cursorPage([item(10)], false);
      throw new Error(`예상하지 못한 cursor ${cursor}`);
    });
    await open();

    await act(async () => button('더 보기')?.click());

    expect(vi.mocked(fetchMemoryFeed).mock.calls.map(([cursor]) => cursor)).toEqual([null, 20]);
    expect(cell(10)).not.toBeNull();
    expect(button('더 보기')).toBeUndefined();
  });

  it('더 보기가 실패하면 받아 둔 기록은 그대로 두고 오류 문구를 보여 준다', async () => {
    vi.mocked(fetchMemoryFeed).mockImplementation(async (cursor) => {
      if (cursor === null) return cursorPage([item(1)], true);
      throw new ApiError(429, 'TOO_MANY_REQUESTS', null);
    });
    await open();

    await act(async () => button('더 보기')?.click());

    expect(cell(1)).not.toBeNull();
    expect(container.querySelector('[role="alert"]')?.textContent).toContain('요청이 너무 많아요');
    expect(button('더 보기')).toBeDefined();
  });
});
