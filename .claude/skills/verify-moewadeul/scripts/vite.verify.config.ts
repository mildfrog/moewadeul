// 검증용 Vite 설정. frontend/vite.config.ts를 그대로 쓰고 포트와 /api 프록시 대상만 검증 전용 백엔드로 바꾼다.
// up.sh가 frontend/ 에서 `npx vite --config <이 파일>`로 실행한다.
import base from '../../../../frontend/vite.config.ts';

export default {
  ...base,
  server: {
    ...base.server,
    port: 15173,
    strictPort: true,
    proxy: { '/api': { target: 'http://127.0.0.1:18080' } },
  },
};
