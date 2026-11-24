package com.moewadeul.basecamp.domain;

import com.moewadeul.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

public enum BasecampErrorCode implements ErrorCode {
    BASECAMP_CAPACITY_INVALID(HttpStatus.BAD_REQUEST, "정원은 캠프 리더를 포함해 2~6명이어야 해요."),
    BASECAMP_OPEN_LIMIT(
            HttpStatus.BAD_REQUEST, "모집 중이거나 마감한 베이스캠프는 최대 3개까지 열 수 있어요. 기존 베이스캠프를 확정하거나 취소한 뒤 다시 시도해 주세요."),
    BASECAMP_SCHEDULE_INVALID(HttpStatus.BAD_REQUEST, "출발일은 내일부터 60일 이내, 일정은 1~3박으로 선택해 주세요."),
    BASECAMP_WARNING_SPOT(HttpStatus.BAD_REQUEST, "공원 구역 주의 표시가 있는 박지에서는 베이스캠프를 열 수 없어요."),
    BASECAMP_CONDITION_NOT_MET(HttpStatus.FORBIDDEN, "이 베이스캠프의 합류 조건에 맞지 않아요."),
    BASECAMP_DATE_CONFLICT(HttpStatus.CONFLICT, "같은 날짜에 이미 참여 중인 베이스캠프가 있어요."),
    BASECAMP_PENDING_LIMIT(HttpStatus.CONFLICT, "이 베이스캠프의 대기 중인 신청이 가득 찼어요. 다른 베이스캠프를 찾아보세요."),
    BASECAMP_REAPPLY_NOT_ALLOWED(HttpStatus.CONFLICT, "이 베이스캠프에는 다시 신청할 수 없어요."),
    BASECAMP_ALREADY_APPLIED(HttpStatus.CONFLICT, "이미 합류 신청했거나 참여 중인 베이스캠프예요."),
    BASECAMP_FULL(HttpStatus.CONFLICT, "정원이 모두 찼어요."),
    BASECAMP_INVALID_STATE(HttpStatus.CONFLICT, "상태가 변경돼 지금은 처리할 수 없어요. 새로고침해 주세요."),
    BASECAMP_NOT_ENOUGH_MEMBERS(HttpStatus.CONFLICT, "멤버가 2명 이상이어야 확정할 수 있어요."),
    BASECAMP_LEADER_CANNOT_LEAVE(HttpStatus.CONFLICT, "캠프 리더는 베이스캠프에서 나갈 수 없어요. 베이스캠프를 취소해 주세요.");

    private final HttpStatus httpStatus;
    private final String message;

    BasecampErrorCode(HttpStatus httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }

    @Override
    public HttpStatus httpStatus() {
        return httpStatus;
    }

    @Override
    public String message() {
        return message;
    }
}
