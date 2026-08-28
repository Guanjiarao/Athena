package athena.cognition.biz.rpc.agent;

/**
 * Transport-level failure when calling athena-cognition-agent (network error,
 * HTTP error, timeout). Follows the CognitionException style: a stable
 * errorCode plus static factories. Business failures reported by the Agent
 * itself are NOT this exception — they arrive as HTTP 200 with
 * status/error fields on the response body.
 *
 * {@link #retryable()} is true only for read timeouts: the Agent workflows are
 * synchronous and long-running, so a timed-out call may be retried (the
 * Agent is idempotent via idempotencyKey). Other transport failures are
 * treated as non-retryable by default.
 */
public class CognitionAgentException extends RuntimeException {

    public static final String AGENT_UNAVAILABLE = "COGNITION_AGENT_UNAVAILABLE";
    public static final String AGENT_READ_TIMEOUT = "COGNITION_AGENT_READ_TIMEOUT";

    private final String errorCode;
    private final String operation;
    private final boolean retryable;

    public CognitionAgentException(String errorCode, String operation,
                                   boolean retryable, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.operation = operation;
        this.retryable = retryable;
    }

    public static CognitionAgentException readTimeout(String operation, Throwable cause) {
        return new CognitionAgentException(AGENT_READ_TIMEOUT, operation, true,
                "cognition-agent 调用读超时（可重试）: " + operation, cause);
    }

    public static CognitionAgentException unavailable(String operation, Throwable cause) {
        return new CognitionAgentException(AGENT_UNAVAILABLE, operation, false,
                "cognition-agent 调用失败: " + operation, cause);
    }

    public String errorCode() {
        return errorCode;
    }

    public String operation() {
        return operation;
    }

    public boolean retryable() {
        return retryable;
    }
}
