package athena.cognition.biz.rpc.agent;

import athena.cognition.biz.rpc.agent.dto.ActionFeedbackWorkflowRequest;
import athena.cognition.biz.rpc.agent.dto.ActionFeedbackWorkflowResponse;
import athena.cognition.biz.rpc.agent.dto.GraphUpdatePreparationRequest;
import athena.cognition.biz.rpc.agent.dto.GraphUpdatePreparationResponse;
import athena.cognition.biz.rpc.agent.dto.IntentClassificationRequest;
import athena.cognition.biz.rpc.agent.dto.IntentClassificationResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Feign client for the athena-cognition-agent AI service (Nacos service name
 * {@code athena-cognition-agent}). The Agent returns plain response DTOs, not
 * the Result<T> envelope used by other internal chains: business failures come
 * back with HTTP 200 and surface through the response body's status/error
 * fields.
 */
@FeignClient(name = "athena-cognition-agent", contextId = "cognitionAgentFeignApi")
public interface CognitionAgentFeignApi {

    @PostMapping("/internal/v1/cognition/nodes/intent-classification")
    IntentClassificationResponse classifyIntent(@RequestBody IntentClassificationRequest request);

    @PostMapping("/internal/v1/cognition/workflows/graph-update/prepare")
    GraphUpdatePreparationResponse prepareGraphUpdate(@RequestBody GraphUpdatePreparationRequest request);

    @PostMapping("/internal/v1/cognition/workflows/action-feedback/prepare")
    ActionFeedbackWorkflowResponse prepareActionFeedback(@RequestBody ActionFeedbackWorkflowRequest request);
}
