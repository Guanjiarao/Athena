package athena.cognition.biz.controller;

import athena.athenaframework.result.Result;
import athena.cognition.biz.domain.CognitionException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice(assignableTypes = CognitionController.class)
public class CognitionExceptionHandler {

    @ExceptionHandler(CognitionException.class)
    public ResponseEntity<Result<Void>> handleDomain(CognitionException ex) {
        return response(ex.status(), ex.code(), ex.getMessage());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
            MissingRequestHeaderException.class})
    public ResponseEntity<Result<Void>> handleValidation(Exception ex) {
        return response(HttpStatus.BAD_REQUEST, "COGNITION_VALIDATION_FAILED", "请求字段不完整或格式不正确");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Result<Void>> handleConflict(DataIntegrityViolationException ex) {
        log.warn("Cognition persistence conflict: {}", ex.getClass().getSimpleName());
        return response(HttpStatus.CONFLICT, "COGNITION_INVALID_STATE_TRANSITION", "请求与当前状态冲突");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleUnexpected(Exception ex) {
        log.error("Unexpected cognition failure", ex);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "COGNITION_INTERNAL_ERROR", "服务暂时不可用，请稍后重试");
    }

    private ResponseEntity<Result<Void>> response(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new Result<>(status.value(), code + ": " + message, null, null));
    }
}
