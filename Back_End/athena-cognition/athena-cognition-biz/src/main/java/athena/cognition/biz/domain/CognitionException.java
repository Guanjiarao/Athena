package athena.cognition.biz.domain;

import org.springframework.http.HttpStatus;

public class CognitionException extends RuntimeException {

    private final String code;
    private final HttpStatus status;

    public CognitionException(String code, String message, HttpStatus status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public static CognitionException badRequest(String message) {
        return new CognitionException("COGNITION_VALIDATION_FAILED", message, HttpStatus.BAD_REQUEST);
    }

    public static CognitionException unauthenticated() {
        return new CognitionException("COGNITION_UNAUTHENTICATED", "请先登录", HttpStatus.UNAUTHORIZED);
    }

    public static CognitionException notFound() {
        return new CognitionException("COGNITION_NOT_FOUND", "资源不存在", HttpStatus.NOT_FOUND);
    }

    public static CognitionException conflict(String code, String message) {
        return new CognitionException(code, message, HttpStatus.CONFLICT);
    }

    public String code() {
        return code;
    }

    public HttpStatus status() {
        return status;
    }
}
