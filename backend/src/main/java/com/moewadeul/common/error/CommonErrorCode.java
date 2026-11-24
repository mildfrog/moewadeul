package com.moewadeul.common.error;

import org.springframework.http.HttpStatus;

public enum CommonErrorCode implements ErrorCode {
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "입력한 내용을 다시 확인해 주세요."),
    AUTHENTICATION_REQUIRED(HttpStatus.UNAUTHORIZED, "로그인한 뒤 다시 시도해 주세요."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "접근 권한이 없어요."),
    MEMBER_NOT_VERIFIED(HttpStatus.FORBIDDEN, "이메일 인증을 마친 뒤 이용할 수 있어요."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "찾는 내용이 없거나 삭제됐어요."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "요청을 처리하지 못했어요. 새로고침한 뒤 다시 시도해 주세요."),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "요청을 처리하지 못했어요. 새로고침한 뒤 다시 시도해 주세요."),
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "요청이 너무 많아요. 잠시 뒤 다시 시도해 주세요."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "일시적인 오류가 발생했어요. 잠시 뒤 다시 시도해 주세요."),
    IMAGE_UPLOAD_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "올린 뒤 아직 쓰지 않은 사진이 너무 많아요. 잠시 뒤 다시 올려 주세요."),
    IMAGE_STORAGE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "지금은 사진을 불러올 수 없어요. 잠시 뒤 다시 시도해 주세요."),
    RESOURCE_BUSY(HttpStatus.SERVICE_UNAVAILABLE, "요청이 몰려 처리하지 못했어요. 잠시 뒤 다시 시도해 주세요."),
    DATA_CONFLICT(HttpStatus.CONFLICT, "다른 요청과 겹쳐 처리하지 못했어요. 새로고침한 뒤 다시 시도해 주세요."),
    IDEMPOTENCY_KEY_REQUIRED(HttpStatus.BAD_REQUEST, "요청을 처리하지 못했어요. 새로고침한 뒤 다시 시도해 주세요."),
    IDEMPOTENCY_KEY_REUSED(HttpStatus.UNPROCESSABLE_CONTENT, "요청을 처리하지 못했어요. 새로고침한 뒤 다시 시도해 주세요."),
    IDEMPOTENCY_IN_PROGRESS(HttpStatus.CONFLICT, "이전 요청을 처리하고 있어요. 잠시만 기다려 주세요.");

    private final HttpStatus httpStatus;
    private final String message;

    CommonErrorCode(HttpStatus httpStatus, String message) {
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
