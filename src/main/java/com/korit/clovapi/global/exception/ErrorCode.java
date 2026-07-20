package com.korit.clovapi.global.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    VALIDATION_FAILED("VALIDATION_FAILED", HttpStatus.BAD_REQUEST, "입력값을 확인해주세요."),
    UNAUTHORIZED("UNAUTHORIZED", HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    FORBIDDEN("FORBIDDEN", HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    NOT_FOUND("NOT_FOUND", HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),
    METHOD_NOT_ALLOWED("METHOD_NOT_ALLOWED", HttpStatus.METHOD_NOT_ALLOWED, "허용되지 않은 HTTP 메서드입니다."),
    INTERNAL_ERROR("INTERNAL_ERROR", HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다."),

    ROOM_MEMBER_NOT_FOUND("ROOM_MEMBER_NOT_FOUND", HttpStatus.FORBIDDEN, "해당 우정공간의 멤버가 아닙니다."),
    NOT_WRITER("NOT_WRITER", HttpStatus.FORBIDDEN, "작성자 본인만 처리할 수 있습니다."),
    ROOM_CAPACITY_EXCEEDED("ROOM_CAPACITY_EXCEEDED", HttpStatus.CONFLICT, "우정공간 정원이 초과되었습니다."),
    INVITE_EXPIRED("INVITE_EXPIRED", HttpStatus.CONFLICT, "초대 코드가 만료되었습니다."),
    INVITE_ALREADY_USED("INVITE_ALREADY_USED", HttpStatus.CONFLICT, "이미 사용한 초대 코드입니다."),
    JOIN_REQUEST_ALREADY_PROCESSED("JOIN_REQUEST_ALREADY_PROCESSED", HttpStatus.CONFLICT, "이미 처리된 가입 신청입니다."),
    JOIN_REQUEST_UNDO_EXPIRED("JOIN_REQUEST_UNDO_EXPIRED", HttpStatus.CONFLICT, "가입 신청 되돌리기 시간이 만료되었습니다."),
    PLAN_NOT_COMPLETED("PLAN_NOT_COMPLETED", HttpStatus.CONFLICT, "완료되지 않은 약속에는 추억을 작성할 수 없습니다."),
    MEMORY_ALREADY_WRITTEN("MEMORY_ALREADY_WRITTEN", HttpStatus.CONFLICT, "이미 해당 약속의 추억을 작성했습니다."),
    STAGE_LOCKED("STAGE_LOCKED", HttpStatus.LOCKED, "이전 단계가 완료되지 않았습니다."),
    STAGE_ALREADY_UPLOADED("STAGE_ALREADY_UPLOADED", HttpStatus.CONFLICT, "해당 단계의 사진이 이미 등록되었습니다."),
    STORAGE_QUOTA_EXCEEDED("STORAGE_QUOTA_EXCEEDED", HttpStatus.INSUFFICIENT_STORAGE, "저장 공간이 부족합니다."),
    MASCOT_INTERACTION_LIMIT_REACHED("MASCOT_INTERACTION_LIMIT_REACHED", HttpStatus.TOO_MANY_REQUESTS, "오늘의 마스코트 교감 횟수를 모두 사용했습니다."),
    RATE_LIMITED("RATE_LIMITED", HttpStatus.TOO_MANY_REQUESTS, "요청 횟수가 제한되었습니다."),

    INVALID_CREDENTIALS("INVALID_CREDENTIALS", HttpStatus.UNAUTHORIZED, "로그인 정보를 확인해주세요."),
    EMAIL_DUPLICATED("EMAIL_DUPLICATED", HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),
    TERMS_REQUIRED("TERMS_REQUIRED", HttpStatus.BAD_REQUEST, "필수 약관에 동의해주세요."),
    INVALID_TOKEN("INVALID_TOKEN", HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다."),
    TOKEN_EXPIRED("TOKEN_EXPIRED", HttpStatus.UNAUTHORIZED, "만료되었거나 무효화된 토큰입니다.");

    private final String code;
    private final HttpStatus httpStatus;
    private final String message;

    ErrorCode(String code, HttpStatus httpStatus, String message) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.message = message;
    }

    public String code() {
        return code;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }

    public String message() {
        return message;
    }
}
