package com.moewadeul.spot.domain;

import com.moewadeul.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

public enum SpotErrorCode implements ErrorCode {
    SPOT_RADIUS_TOO_LARGE(HttpStatus.BAD_REQUEST, "검색 반경은 최대 50km까지 선택할 수 있어요."),
    BAKJI_ALREADY_CONFIRMED(HttpStatus.CONFLICT, "이미 현장 확인을 완료한 박지예요."),
    BAKJI_ALREADY_REPORTED(HttpStatus.CONFLICT, "이미 신고한 박지예요."),
    SPOT_INVALID_STATE(HttpStatus.CONFLICT, "상태가 변경돼 지금은 처리할 수 없어요. 새로고침해 주세요."),
    BAKJI_CONCURRENTLY_MODIFIED(HttpStatus.CONFLICT, "상태가 변경돼 지금은 처리할 수 없어요. 새로고침해 주세요.");

    private final HttpStatus httpStatus;
    private final String message;

    SpotErrorCode(HttpStatus httpStatus, String message) {
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
