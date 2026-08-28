package athena.cognition.biz.rpc.agent;

import athena.cognition.biz.rpc.agent.dto.ActionFeedbackWorkflowRequest;
import athena.cognition.biz.rpc.agent.dto.ActionFeedbackWorkflowResponse;
import athena.cognition.biz.rpc.agent.dto.GraphUpdatePreparationRequest;
import athena.cognition.biz.rpc.agent.dto.GraphUpdatePreparationResponse;
import athena.cognition.biz.rpc.agent.dto.IntentClassificationRequest;
import athena.cognition.biz.rpc.agent.dto.IntentClassificationResponse;
import feign.FeignException;
import feign.RetryableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * Thin wrapper over {@link CognitionAgentFeignApi} so callers never deal with
 * Feign exceptions: transport failures are logged and rethrown as
 * {@link CognitionAgentException}. Read timeouts are reported separately as
 * retryable ({@link CognitionAgentException#retryable()}); Agent-side business
 * failures are untouched and stay on the response body (HTTP 200 + status/error).
 */
@Slf4j
@Component
public class CognitionAgentClient {

    private final CognitionAgentFeignApi agentApi;

    public CognitionAgentClient(CognitionAgentFeignApi agentApi) {
        this.agentApi = agentApi;
    }

    public IntentClassificationResponse classifyIntent(IntentClassificationRequest request) {
        return call("intent-classification", () -> agentApi.classifyIntent(request));
    }

    public GraphUpdatePreparationResponse prepareGraphUpdate(GraphUpdatePreparationRequest request) {
        return call("graph-update/prepare", () -> agentApi.prepareGraphUpdate(request));
    }

    public ActionFeedbackWorkflowResponse prepareActionFeedback(ActionFeedbackWorkflowRequest request) {
        return call("action-feedback/prepare", () -> agentApi.prepareActionFeedback(request));
    }

    private <T> T call(String operation, Supplier<T> invocation) {
        try {
            return invocation.get();
        } catch (RetryableException e) {
            // RetryableException covers connect/read timeouts of the default
            // Feign client; must be caught before FeignException (its parent).
            log.warn("cognition-agent call timed out, operation={}", operation, e);
            throw CognitionAgentException.readTimeout(operation, e);
        } catch (FeignException e) {
            log.warn("cognition-agent call failed, operation={}, status={}", operation, e.status(), e);
            throw CognitionAgentException.unavailable(operation, e);
        } catch (RuntimeException e) {
            log.warn("cognition-agent call failed, operation={}", operation, e);
            throw CognitionAgentException.unavailable(operation, e);
        }
    }
}
