import type { NotificationType } from '../../api/types';

export const NOTIFICATION_LABELS: Record<NotificationType, string> = {
  BASECAMP_APPLIED: '베이스캠프 새 합류 신청',
  BASECAMP_APPROVED: '베이스캠프 합류 승인',
  BASECAMP_REJECTED: '베이스캠프 합류 거절',
  BASECAMP_KICKED: '베이스캠프 퇴장 처리',
  BASECAMP_CONFIRMED: '베이스캠프 확정',
  BASECAMP_CANCELED: '베이스캠프 취소',
  BASECAMP_COMPLETED: '동행 후기 작성 안내',
  BASECAMP_MEMBER_CHANGED: '베이스캠프 멤버 변동',
  MEMBER_REPORT_RESOLVED: '신고 처리 결과',
  SANCTION_CONFIRMED: '이용 제재 안내',
  PROGRAM_APPLICATION_CONFIRMED: '공식 행사 신청 확정',
  PROGRAM_APPLICATION_CANCELED: '공식 행사 신청 취소',
  PROGRAM_APPLICATION_EXPIRED: '공식 행사 결제 기한 만료',
  PROGRAM_SEAT_RELEASED: '공식 행사 빈자리 알림',
};

export const NOTIFICATION_TYPES = Object.keys(NOTIFICATION_LABELS) as NotificationType[];
