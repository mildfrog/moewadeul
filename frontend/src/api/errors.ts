import { ApiError } from './client';

const REQUEST_BROKEN = '요청을 처리하지 못했어요. 새로고침한 뒤 다시 시도해 주세요.';
const STATE_CHANGED = '상태가 변경돼 지금은 처리할 수 없어요. 새로고침해 주세요.';

const ERROR_MESSAGES: Record<string, string> = {
  INVALID_INPUT: '입력한 내용을 다시 확인해 주세요.',
  AUTHENTICATION_REQUIRED: '로그인한 뒤 다시 시도해 주세요.',
  ACCESS_DENIED: '접근 권한이 없어요.',
  NOT_FOUND: '찾는 내용이 없거나 삭제됐어요.',
  TOO_MANY_REQUESTS: '요청이 너무 많아요. 잠시 뒤 다시 시도해 주세요.',
  METHOD_NOT_ALLOWED: REQUEST_BROKEN,
  UNSUPPORTED_MEDIA_TYPE: REQUEST_BROKEN,
  INTERNAL_ERROR: '일시적인 오류가 발생했어요. 잠시 뒤 다시 시도해 주세요.',
  IMAGE_STORAGE_UNAVAILABLE: '지금은 사진을 불러올 수 없어요. 잠시 뒤 다시 시도해 주세요.',
  IDEMPOTENCY_KEY_REQUIRED: REQUEST_BROKEN,
  IDEMPOTENCY_KEY_REUSED: REQUEST_BROKEN,
  IDEMPOTENCY_IN_PROGRESS: '이전 요청을 처리하고 있어요. 잠시만 기다려 주세요.',

  MEMBER_EMAIL_DUPLICATED: '이미 가입된 이메일이에요. 로그인하거나 다른 이메일을 입력해 주세요.',
  MEMBER_NICKNAME_DUPLICATED: '이미 사용 중인 닉네임이에요. 다른 닉네임을 입력해 주세요.',
  MEMBER_DISPOSABLE_EMAIL: '일회용 이메일은 사용할 수 없어요.',
  SIGNUP_LIMITED: '이 네트워크에서 가입 요청이 너무 많아요. 잠시 뒤 다시 시도해 주세요.',
  MEMBER_PASSWORD_POLICY: '비밀번호 규칙에 맞지 않아요. 안내에 맞춰 다시 입력해 주세요.',
  MEMBER_CURRENT_PASSWORD_MISMATCH: '현재 비밀번호가 맞지 않아요.',
  MEMBER_PASSWORD_UNCHANGED: '현재 사용 중인 비밀번호와 같아요. 다른 비밀번호를 입력해 주세요.',
  MEMBER_NOT_VERIFIED: '이메일 인증을 마친 뒤 이용할 수 있어요.',
  EMAIL_CODE_INVALID: '인증 코드가 맞지 않아요. 다시 확인해 주세요.',
  EMAIL_CODE_EXPIRED: '인증 코드가 만료됐어요. 새 코드를 받아 주세요.',
  EMAIL_CODE_ATTEMPTS_EXCEEDED: '입력 횟수를 초과했어요. 새 인증 코드를 받아 주세요.',
  EMAIL_ALREADY_VERIFIED: '이미 이메일 인증을 완료했어요.',
  EMAIL_RESEND_LIMITED: '인증 코드를 너무 자주 요청했어요. 잠시 뒤 다시 시도해 주세요.',
  LOGIN_FAILED: '이메일 또는 비밀번호가 맞지 않아요.',
  LOGIN_LOCKED: '로그인에 여러 번 실패해 계정이 일시 잠겼어요. 잠시 뒤 다시 시도해 주세요.',
  MEMBER_SUSPENDED: '이용이 정지된 계정이에요. 정지가 해제된 뒤 다시 로그인해 주세요.',
  PASSWORD_RESET_TOKEN_INVALID: '만료됐거나 이미 사용한 링크예요. 비밀번호 재설정을 다시 요청해 주세요.',
  PASSWORD_RESET_LIMITED: '재설정 요청이 너무 많아요. 잠시 뒤 다시 시도해 주세요.',

  IDENTITY_ALREADY_VERIFIED: '이미 본인확인을 마쳤어요.',
  IDENTITY_CI_DUPLICATED: '다른 계정으로 이미 본인확인을 마쳤어요. 해당 계정으로 로그인해 주세요.',
  TRUST_LEVEL_INSUFFICIENT: '신뢰 단계가 부족해요. 내 정보에서 단계 조건을 확인할 수 있어요.',
  COMPANION_REVIEW_NOT_ELIGIBLE: '함께 다녀온 멤버에게만 동행 후기를 남길 수 있어요.',
  COMPANION_REVIEW_DEADLINE_PASSED: '동행 후기 작성 기한이 지났어요.',
  COMPANION_REVIEW_DUPLICATED: '이미 이 멤버에게 동행 후기를 남겼어요.',
  COMPANION_REVIEW_IMMUTABLE: '작성한 동행 후기는 수정하거나 삭제할 수 없어요.',
  REPORT_NOT_ELIGIBLE: '같은 베이스캠프에 신청했거나 함께한 회원만 신고할 수 있어요.',
  REPORT_DUPLICATED: '이미 신고를 접수했어요.',
  REPORT_INVALID_STATE: '이미 처리된 신고예요. 새로고침해 주세요.',
  SANCTION_INVALID_STATE: '이미 해제됐거나 만료된 제재예요. 새로고침해 주세요.',

  SPOT_RADIUS_TOO_LARGE: '검색 반경은 최대 50km까지 선택할 수 있어요.',
  BAKJI_ALREADY_CONFIRMED: '이미 현장 확인을 완료한 박지예요.',
  BAKJI_ALREADY_REPORTED: '이미 신고한 박지예요.',
  SPOT_INVALID_STATE: STATE_CHANGED,
  BAKJI_CONCURRENTLY_MODIFIED: STATE_CHANGED,
  AI_SEARCH_LIMITED: '오늘 AI 검색 횟수를 모두 사용했어요. 지역 이름으로 검색해 주세요.',
  AI_SEARCH_UNAVAILABLE: '지금은 AI 검색을 이용할 수 없어요. 지역 이름으로 검색해 주세요.',
  SPOT_REVIEW_DUPLICATED: '같은 방문일로 이미 후기를 작성했어요. 다른 날짜를 선택해 주세요.',

  BASECAMP_OPEN_LIMIT:
    '모집 중이거나 마감한 베이스캠프는 최대 3개까지 열 수 있어요. 기존 베이스캠프를 확정하거나 취소한 뒤 다시 시도해 주세요.',
  BASECAMP_CAPACITY_INVALID: '정원은 캠프 리더를 포함해 2~6명이어야 해요.',
  BASECAMP_SCHEDULE_INVALID: '출발일은 내일부터 60일 이내, 일정은 1~3박으로 선택해 주세요.',
  BASECAMP_WARNING_SPOT: '공원 구역 주의 표시가 있는 박지에서는 베이스캠프를 열 수 없어요.',
  BASECAMP_CONDITION_NOT_MET: '이 베이스캠프의 합류 조건에 맞지 않아요.',
  BASECAMP_DATE_CONFLICT: '같은 날짜에 이미 참여 중인 베이스캠프가 있어요.',
  BASECAMP_REAPPLY_NOT_ALLOWED: '이 베이스캠프에는 다시 신청할 수 없어요.',
  BASECAMP_PENDING_LIMIT: '이 베이스캠프의 대기 중인 신청이 가득 찼어요. 다른 베이스캠프를 찾아보세요.',
  BASECAMP_ALREADY_APPLIED: '이미 합류 신청했거나 참여 중인 베이스캠프예요.',
  BASECAMP_FULL: '정원이 모두 찼어요.',
  BASECAMP_INVALID_STATE: STATE_CHANGED,
  BASECAMP_NOT_ENOUGH_MEMBERS: '멤버가 2명 이상이어야 확정할 수 있어요.',
  BASECAMP_LEADER_CANNOT_LEAVE: '캠프 리더는 베이스캠프에서 나갈 수 없어요. 베이스캠프를 취소해 주세요.',

  PROGRAM_NOT_IN_APPLY_PERIOD: '지금은 신청 기간이 아니에요.',
  PROGRAM_SOLD_OUT: '남은 자리가 없어요. 빈자리 알림을 신청해 보세요.',
  PROGRAM_SEATS_AVAILABLE: '남은 자리가 있어요. 바로 신청해 주세요.',
  PROGRAM_ALREADY_APPLIED: '이미 신청한 행사예요. 내 행사 신청에서 확인할 수 있어요.',
  PROGRAM_PAYMENT_EXPIRED: '결제 기한이 지났어요. 남은 자리가 있으면 다시 신청해 주세요.',
  PROGRAM_INVALID_STATE: STATE_CHANGED,
  PROGRAM_CANCEL_NOT_ALLOWED: '취소 가능 기한이 지났어요. 관리자에게 문의해 주세요.',
  PROGRAM_ENDED: '이미 종료된 행사는 취소할 수 없어요.',
  PROGRAM_CAPACITY_DECREASE: '신청이 시작됐거나 신청자가 있는 행사는 정원을 줄일 수 없어요.',

  COMMUNITY_ALREADY_REPORTED: '이미 신고한 글이나 댓글이에요.',
  MEMORY_ALREADY_REPORTED: '이미 신고한 기록이에요.',
  SOLO_TRIP_SCHEDULE_INVALID: '출발일은 내일부터 60일 이내, 일정은 1~3박으로 선택해 주세요.',
  SOLO_TRIP_LIMIT: '예정된 혼자 가는 일정은 최대 3개까지 등록할 수 있어요. 내 정보에서 기존 일정을 정리해 주세요.',
  SOLO_TRIP_OVERLAP: '등록된 다른 일정과 날짜가 겹쳐요.',

  COMMUNITY_INVALID_STATE: STATE_CHANGED,
  MEMORY_INVALID_STATE: STATE_CHANGED,
  IMAGE_UPLOAD_FAILED: '사진을 올리지 못했어요. 다시 시도해 주세요.',

  SYNC_JOB_ALREADY_RUNNING: '같은 작업이 이미 실행 중이에요. 끝난 뒤 다시 시도해 주세요.',

  NETWORK_ERROR: '인터넷 연결이 원활하지 않아요. 연결 상태를 확인한 뒤 다시 시도해 주세요.',
  REQUEST_TIMEOUT: '응답 시간이 초과됐어요. 연결 상태를 확인한 뒤 다시 시도해 주세요.',
};

export const UNCONFIRMED_TITLE = '처리 결과를 확인하지 못했어요';

const UNEXPECTED_ERROR_MESSAGE = '일시적인 오류가 발생했어요. 잠시 뒤 다시 시도해 주세요.';

export function errorMessage(code: string): string | undefined {
  return ERROR_MESSAGES[code];
}

export function toUserMessage(error: unknown): string {
  if (error instanceof ApiError) {
    return error.message;
  }
  return UNEXPECTED_ERROR_MESSAGE;
}

export function serverTraceId(error: unknown): string | undefined {
  return error instanceof ApiError && error.status >= 500 ? error.traceId : undefined;
}
