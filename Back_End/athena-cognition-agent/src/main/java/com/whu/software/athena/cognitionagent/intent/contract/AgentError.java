package com.whu.software.athena.cognitionagent.intent.contract;

public class AgentError {

    public AgentErrorCode code;
    public String message;
    public boolean retryable;
    public String field;
    public Object details;

    public AgentError() {
    }

    public AgentError(AgentErrorCode code,
                      String message,
                      boolean retryable,
                      String field,
                      Object details) {
        this.code = code;
        this.message = message;
        this.retryable = retryable;
        this.field = field;
        this.details = details;
    }
}
