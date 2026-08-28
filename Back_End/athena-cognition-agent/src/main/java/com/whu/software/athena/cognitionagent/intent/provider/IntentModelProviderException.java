package com.whu.software.athena.cognitionagent.intent.provider;

import com.whu.software.athena.cognitionagent.intent.contract.AgentErrorCode;

/** Maps model transport and output failures to the Agent error vocabulary. */
public class IntentModelProviderException extends RuntimeException {

    private final AgentErrorCode errorCode;
    private final boolean retryable;

    public IntentModelProviderException(AgentErrorCode errorCode, String message,
                                        boolean retryable) {
        super(message);
        this.errorCode = errorCode;
        this.retryable = retryable;
    }

    public AgentErrorCode errorCode() {
        return errorCode;
    }

    public boolean retryable() {
        return retryable;
    }
}
