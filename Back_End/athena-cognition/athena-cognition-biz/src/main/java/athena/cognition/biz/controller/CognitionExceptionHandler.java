package athena.cognition.biz.controller;

import athena.athenaframework.result.Result;
import athena.cognition.biz.domain.CognitionException;
import athena.cognition.biz.domain.CognitionModels.ErrorBody;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Section 12: handled business errors keep HTTP 200, the semantic value goes to
 * the outer Result.code and the stable errorCode to data.errorCode.
 */
@Slf4j
@RestControllerAdvice(assignableTypes = CognitionController.class)
public class CognitionExceptionHandler {

    @ExceptionHandler(CognitionException.class)
    public Result<ErrorBody> handleDomain(CognitionException ex) {
        ErrorBody body = ex.errorCode() == null ? null
                : new ErrorBody(ex.errorCode(), ex.objectId(), ex.currentStatus());
        return Result.fail(ex.semanticCode(), ex.getMessage(), body);
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
    public Result<ErrorBody> handleValidation(Exception ex) {
        return Result.fail(400, "请求字段不完整或格式不正确",
                ErrorBody.of(CognitionException.INVALID_ARGUMENT));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public Result<ErrorBody> handleConflict(DataIntegrityViolationException ex) {
        log.warn("Cognition persistence conflict: {}", ex.getClass().getSimpleName());
        return Result.fail(409, "请求与当前状态冲突", ErrorBody.of(CognitionException.STATE_CONFLICT));
    }

    @ExceptionHandler(Exception.class)
    public Result<ErrorBody> handleUnexpected(Exception ex) {
        log.error("Unexpected cognition failure", ex);
        return Result.fail(500, "服务暂时不可用，请稍后重试",
                ErrorBody.of(CognitionException.GENERATION_FAILED));
    }
}
