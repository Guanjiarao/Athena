package athena.cognition.biz.rpc.agent.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Mirror of the Agent's AgentError. Business failures are returned with HTTP
 * 200 and surface through this object on the response body.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AgentError {

    public AgentErrorCode code;
    public String message;
    public boolean retryable;
    public String field;
    public Object details;
}
