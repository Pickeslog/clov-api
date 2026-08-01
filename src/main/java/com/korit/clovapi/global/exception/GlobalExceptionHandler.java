package com.korit.clovapi.global.exception;

import com.korit.clovapi.global.response.ApiResponse;
import com.korit.clovapi.global.response.ApiErrorDetail;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ApiResponse<Void>> handleDomainException(DomainException exception) {
        // details는 대부분 null이고, ApiError가 NON_NULL이라 그때는 응답에서 통째로 생략된다.
        return failure(exception.errorCode(), exception.details());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException exception
    ) {
        List<ApiErrorDetail> details = exception.getBindingResult().getFieldErrors().stream()
                .map(this::toErrorDetail)
                .toList();

        return failure(ErrorCode.VALIDATION_FAILED, details);
    }

    @ExceptionHandler({ConstraintViolationException.class, MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class})
    public ResponseEntity<ApiResponse<Void>> handleValidationFailure(Exception exception) {
        return failure(ErrorCode.VALIDATION_FAILED);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthenticationException(AuthenticationException exception) {
        return failure(ErrorCode.UNAUTHORIZED);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDeniedException(AccessDeniedException exception) {
        return failure(ErrorCode.FORBIDDEN);
    }

    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<ApiResponse<Void>> handleNotFoundException(Exception exception) {
        return failure(ErrorCode.NOT_FOUND);
    }

    // 유니크 제약 위반의 최종 방어선(clov-api#98) — 도메인 서비스가 미리 막지 못한 경합(동시 요청)
    // 등으로 DB가 대신 막을 때 500이 아니라 409로 나가게 한다. Exception.class보다 먼저 매칭된다.
    //
    // 상위 DataIntegrityViolationException이 아니라 DuplicateKeyException으로 좁힌 이유: 상위는
    // NOT NULL 위반·FK 위반까지 포함하는데, 그건 클라이언트 잘못이 아니라 서버 버그다. 그것까지
    // 409 "이미 존재하는 데이터와 충돌합니다"로 내보내면 진짜 버그가 500을 안 내고 엉뚱한 메시지로
    // 위장돼 아무도 버그로 인지하지 못한다. 중복 키만 409로 바꾸고 나머지는 500으로 남긴다.
    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicateKeyException(
            DuplicateKeyException exception
    ) {
        return failure(ErrorCode.DUPLICATE_RESOURCE);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotAllowedException(
            HttpRequestMethodNotSupportedException exception
    ) {
        return failure(ErrorCode.METHOD_NOT_ALLOWED);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpectedException(Exception exception) {
        return failure(ErrorCode.INTERNAL_ERROR);
    }

    private ResponseEntity<ApiResponse<Void>> failure(ErrorCode errorCode) {
        return ResponseEntity.status(errorCode.httpStatus())
                .body(ApiResponse.failure(errorCode.code(), errorCode.message()));
    }

    private ResponseEntity<ApiResponse<Void>> failure(ErrorCode errorCode, List<ApiErrorDetail> details) {
        return ResponseEntity.status(errorCode.httpStatus())
                .body(ApiResponse.failure(errorCode.code(), errorCode.message(), details));
    }

    private ApiErrorDetail toErrorDetail(FieldError fieldError) {
        String reason = fieldError.getDefaultMessage();
        if (reason == null || reason.isBlank()) {
            reason = ErrorCode.VALIDATION_FAILED.message();
        }

        return new ApiErrorDetail(fieldError.getField(), reason);
    }
}
