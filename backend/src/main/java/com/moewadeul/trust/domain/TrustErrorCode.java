package com.moewadeul.trust.domain;

import com.moewadeul.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

public enum TrustErrorCode implements ErrorCode {
    IDENTITY_ALREADY_VERIFIED(HttpStatus.CONFLICT, "이미 본인확인을 마쳤어요."),
    IDENTITY_CI_DUPLICATED(HttpStatus.CONFLICT, "다른 계정으로 이미 본인확인을 마쳤어요. 해당 계정으로 로그인해 주세요."),
    IDENTITY_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "지금은 본인확인을 할 수 없어요. 잠시 뒤 다시 시도해 주세요."),
    TRUST_LEVEL_INSUFFICIENT(HttpStatus.FORBIDDEN, "신뢰 단계가 부족해요. 내 정보에서 단계 조건을 확인할 수 있어요."),
    COMPANION_REVIEW_NOT_ELIGIBLE(HttpStatus.FORBIDDEN, "함께 다녀온 멤버에게만 동행 후기를 남길 수 있어요."),
    COMPANION_REVIEW_DEADLINE_PASSED(HttpStatus.BAD_REQUEST, "동행 후기 작성 기한이 지났어요."),
    COMPANION_REVIEW_DUPLICATED(HttpStatus.CONFLICT, "이미 이 멤버에게 동행 후기를 남겼어요."),
    REPORT_NOT_ELIGIBLE(HttpStatus.FORBIDDEN, "같은 베이스캠프에 신청했거나 함께한 회원만 신고할 수 있어요."),
    REPORT_DUPLICATED(HttpStatus.CONFLICT, "이미 신고를 접수했어요."),
    REPORT_INVALID_STATE(HttpStatus.CONFLICT, "이미 처리된 신고예요. 새로고침해 주세요."),
    SANCTION_INVALID_STATE(HttpStatus.CONFLICT, "이미 해제됐거나 만료된 제재예요. 새로고침해 주세요.");

    private final HttpStatus httpStatus;
    private final String message;

    TrustErrorCode(HttpStatus httpStatus, String message) {
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
