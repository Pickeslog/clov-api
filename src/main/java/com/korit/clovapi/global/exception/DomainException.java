package com.korit.clovapi.global.exception;

import com.korit.clovapi.global.response.ApiErrorDetail;

import java.util.List;

public class DomainException extends RuntimeException {

    private final ErrorCode errorCode;
    private final List<ApiErrorDetail> details;

    public DomainException(ErrorCode errorCode) {
        this(errorCode, null);
    }

    /**
     * 클라이언트가 분기에 써야 하는 값을 에러와 함께 실어 보낼 때 쓴다.
     * 계약 §2에 따라 <b>계약에 명시된 도메인 에러에만</b> 허용된다
     * (현재 명시 목록: ROOM_MEMBER_ALREADY_JOINED의 roomId).
     * 임의로 details를 붙이지 말 것.
     */
    public DomainException(ErrorCode errorCode, List<ApiErrorDetail> details) {
        super(errorCode.message());
        this.errorCode = errorCode;
        this.details = details;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    /** 없으면 null — ApiError가 NON_NULL이라 응답에서 details 자체가 생략된다. */
    public List<ApiErrorDetail> details() {
        return details;
    }
}
