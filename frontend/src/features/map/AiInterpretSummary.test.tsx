import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it } from 'vitest';
import { AiInterpretSummary } from './AiInterpretSummary';

describe('AI 해석 요약', () => {
  it('[F-27][AI-08] AI 검색 결과 표시를 붙인다', () => {
    const html = renderToStaticMarkup(<AiInterpretSummary summary={{ unparsed: [], empty: false }} />);

    expect(html).toContain('AI 검색 결과');
    expect(html).toContain('입력한 조건으로 검색했어요.');
  });

  it('[F-27][AI-05][AI-08] 반영되지 않은 내용과 찾지 못한 지역, 제외된 입지를 함께 알린다', () => {
    const html = renderToStaticMarkup(
      <AiInterpretSummary
        summary={{ unparsed: ['다음 주말'], missingRegion: '없는곳', droppedSettings: ['VALLEY'], empty: false }}
      />,
    );

    expect(html).toContain('반영되지 않은 내용: 다음 주말');
    expect(html).toContain('없는곳');
    expect(html).toContain('계곡');
  });
});
