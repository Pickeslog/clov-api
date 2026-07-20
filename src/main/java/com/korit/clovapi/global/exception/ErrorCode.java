package com.korit.clovapi.global.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "요청 값 검증에 실패했습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다."),

    ROOM_MEMBER_NOT_FOUND(HttpStatus.FORBIDDEN, "해당 우정공간의 멤버가 아닙니다."),
    NOT_WRITER(HttpStatus.FORBIDDEN, "작성자 본인만 처리할 수 있습니다."),
    ROOM_CAPACITY_EXCEEDED(HttpStatus.CONFLICT, "우정공간 정원이 초과되었습니다."),
    INVITE_EXPIRED(HttpStatus.CONFLICT, "초대 코드가 만료되었습니다."),
    INVITE_ALREADY_USED(HttpStatus.CONFLICT, "이미 사용한 초대 코드입니다."),
    JOIN_REQUEST_ALREADY_PROCESSED(HttpStatus.CONFLICT, "이미 처리된 가입 신청입니다."),
    JOIN_REQUEST_UNDO_EXPIRED(HttpStatus.CONFLICT, "가입 신청 되돌리기 시간이 만료되었습니다."),
    PLAN_NOT_COMPLETED(HttpStatus.CONFLICT, "완료되지 않은 약속에는 추억을 작성할 수 없습니다."),
    MEMORY_ALREADY_WRITTEN(HttpStatus.CONFLICT, "이미 해당 약속의 추억을 작성했습니다."),
    STAGE_LOCKED(HttpStatus.LOCKED, "이전 단계가 완료되지 않았습니다."),
    STAGE_ALREADY_UPLOADED(HttpStatus.CONFLICT, "해당 단계의 사진이 이미 등록되었습니다."),
    STORAGE_QUOTA_EXCEEDED(HttpStatus.INSUFFICIENT_STORAGE, "저장 공간이 부족합니다."),
    MASCOT_INTERACTION_LIMIT_REACHED(HttpStatus.TOO_MANY_REQUESTS, "오늘의 마스코트 교감 횟수를 모두 사용했습니다."),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "요청 횟수가 제한되었습니다.");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus status() {
        return status;
    }

    public String message() {
        return message;
    }
}
