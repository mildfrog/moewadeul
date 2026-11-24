import { describe, expect, it } from 'vitest';
import { ApiError } from './client';
import { errorMessage, toUserMessage } from './errors';

describe('베이스캠프 한도 오류 문구', () => {
  it('BASECAMP_OPEN_LIMIT은 마감한 베이스캠프도 개수에 든다고 알리고 확정이나 취소를 안내한다', () => {
    const expected =
      '모집 중이거나 마감한 베이스캠프는 최대 3개까지 열 수 있어요. 기존 베이스캠프를 확정하거나 취소한 뒤 다시 시도해 주세요.';

    expect(errorMessage('BASECAMP_OPEN_LIMIT')).toBe(expected);
    expect(toUserMessage(new ApiError(409, 'BASECAMP_OPEN_LIMIT', null))).toBe(expected);
  });

  it('BASECAMP_PENDING_LIMIT은 이 베이스캠프의 신청이 가득 찼다고 알리고 다른 베이스캠프를 안내한다', () => {
    const expected = '이 베이스캠프의 대기 중인 신청이 가득 찼어요. 다른 베이스캠프를 찾아보세요.';

    expect(errorMessage('BASECAMP_PENDING_LIMIT')).toBe(expected);
    expect(toUserMessage(new ApiError(409, 'BASECAMP_PENDING_LIMIT', null))).toBe(expected);
  });
});
