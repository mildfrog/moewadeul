-- 늦게 도착하면 쓸모없는 알림 메일의 유효기간이다(EV-09). 예를 들어 베이스캠프 확정·취소 메일은 출발일 0시(한국 시간)가 지나면 보내지 않는다.
-- 메일 발송 작업이 집을 때 기한이 지났으면 SKIPPED로 바꾼다. NULL이면 기한이 없다. 시각은 V1과 같이 DATETIME(6) UTC다.
ALTER TABLE notification ADD COLUMN email_expires_at DATETIME(6) NULL AFTER email_bulk;
