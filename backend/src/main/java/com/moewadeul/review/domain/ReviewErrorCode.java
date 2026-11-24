package com.moewadeul.review.domain;

import com.moewadeul.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

public enum ReviewErrorCode implements ErrorCode {
    SPOT_REVIEW_DUPLICATED(HttpStatus.CONFLICT, "같은 방문일로 이미 후기를 작성했어요. 다른 날짜를 선택해 주세요.");

    private final HttpStatus httpStatus;
    private final String message;

    ReviewErrorCode(HttpStatus httpStatus, String message) {
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
