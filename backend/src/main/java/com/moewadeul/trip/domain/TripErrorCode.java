package com.moewadeul.trip.domain;

import com.moewadeul.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

public enum TripErrorCode implements ErrorCode {
    SOLO_TRIP_SCHEDULE_INVALID(HttpStatus.BAD_REQUEST, "출발일은 내일부터 60일 이내, 일정은 1~3박으로 선택해 주세요."),
    SOLO_TRIP_LIMIT(HttpStatus.CONFLICT, "예정된 혼자 가는 일정은 최대 3개까지 등록할 수 있어요. 내 정보에서 기존 일정을 정리해 주세요."),
    SOLO_TRIP_OVERLAP(HttpStatus.CONFLICT, "등록된 다른 일정과 날짜가 겹쳐요.");

    private final HttpStatus httpStatus;
    private final String message;

    TripErrorCode(HttpStatus httpStatus, String message) {
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
