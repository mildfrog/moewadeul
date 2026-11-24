import type { ParkWarning } from '../api/types';
import { Evidence } from './Evidence';
import { Notice } from './Notice';
import { sourceLabel } from '../lib/spotLabels';

const FALLBACK_NOTICE = '공원 경계는 참고용 자료예요. 정확한 경계는 공식 고시 도면을 확인해 주세요.';
const FALLBACK_GUIDE =
  '자연공원 내 지정 야영장 외 구역에서는 야영과 취사가 금지되며 과태료가 부과될 수 있어요. 가까운 지정 야영장을 이용해 주세요.';

export function ParkWarningNotice({ warning }: { warning: ParkWarning }) {
  if (!warning.warned) return null;
  return (
    <Notice tone="warning" title="자연공원 구역일 수 있어요">
      <div className="flex flex-col gap-2">
        {warning.areaName && <p className="font-semibold">{warning.areaName}</p>}
        <p>{warning.notice ?? FALLBACK_NOTICE}</p>
        <p>{warning.guide ?? FALLBACK_GUIDE}</p>
        <Evidence
          items={[
            ...(warning.source ? [{ label: '출처', value: sourceLabel(warning.source) }] : []),
            ...(warning.sourceDate ? [{ label: '기준일', value: warning.sourceDate }] : []),
          ]}
        />
      </div>
    </Notice>
  );
}
