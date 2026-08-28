package athena.cognition.biz.agenttask.mq;

import athena.athenaframework.mq.MessageWrapper;
import athena.athenaframework.utils.JsonUtils;
import athena.cognition.biz.agenttask.AgentTaskPayload;
import athena.cognition.biz.agenttask.AgentTaskWorker;
import athena.cognition.biz.repository.CognitionAgentJdbcRepository;
import athena.cognition.biz.repository.CognitionAgentJdbcRepository.AgentTaskRow;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Parses the dispatch message and delegates to {@link AgentTaskWorker} on the
 * consumer callback thread (worker 内部本来就是同步的，照 count 的模式不再
 * 单独开线程池）.
 *
 * <p>Duplicate delivery is safe: the worker skips tasks not in PENDING/FAILED.
 * A missing task row or unresolvable payload is ACKed (reconsuming cannot fix
 * it); transient failures (DB hiccup, payload parse blowup) bubble up so the
 * listener returns RECONSUME_LATER.
 */
@Slf4j
@Component
public class AgentTaskConsumer {

    private static final String TRIGGER_ACTION_FEEDBACK = "ACTION_FEEDBACK";

    private final AgentTaskWorker worker;
    private final CognitionAgentJdbcRepository agentRepository;
    private final ObjectMapper objectMapper;

    public AgentTaskConsumer(AgentTaskWorker worker, CognitionAgentJdbcRepository agentRepository,
                             ObjectMapper objectMapper) {
        this.worker = worker;
        this.agentRepository = agentRepository;
        this.objectMapper = objectMapper;
    }

    public void onMessage(String body) {
        MessageWrapper<AgentTaskMessage> wrapper = JsonUtils.parseObject(
                body, new TypeReference<MessageWrapper<AgentTaskMessage>>() {
                });
        AgentTaskMessage message = wrapper == null ? null : wrapper.getBody();
        if (message == null || message.taskId() == null) {
            log.warn("[AgentTaskConsumer] 消息体为空, body={}", body);
            return;
        }
        AgentTaskRow task = agentRepository.findTaskByTaskId(message.taskId()).orElse(null);
        if (task == null) {
            log.warn("[AgentTaskConsumer] 任务不存在, taskId={}", message.taskId());
            return;
        }
        AgentTaskPayload payload = resolvePayload(task);
        if (payload == null) {
            return;
        }
        if (TRIGGER_ACTION_FEEDBACK.equals(task.triggerType())) {
            worker.executeFeedbackTask(task.taskId(), payload.toFeedbackContext());
        } else {
            worker.executeGraphTask(task.taskId(), payload.toGraphContext(task.triggerType()));
        }
    }

    /**
     * Rebuilds the execution context from payload_json. Rows created before the
     * column existed (still PENDING after the upgrade) get one fallback: clue
     * tasks parse the clue id back out of the idempotency key
     * (clue:{clueId}:{workflowVersion}). Anything else cannot be recovered and
     * is ACKed with an error log.
     */
    private AgentTaskPayload resolvePayload(AgentTaskRow task) {
        String json = agentRepository.findTaskPayload(task.taskId()).orElse(null);
        if (json != null) {
            try {
                return objectMapper.readValue(json, AgentTaskPayload.class);
            } catch (Exception ex) {
                throw new IllegalStateException("cannot parse task payload: " + task.taskId(), ex);
            }
        }
        if ("CLUE_CREATED".equals(task.triggerType())) {
            String key = task.idempotencyKey();
            int end = key == null ? -1 : key.lastIndexOf(':');
            if (key != null && key.startsWith("clue:") && end > "clue:".length()) {
                return AgentTaskPayload.forGraph(key.substring("clue:".length(), end), null, null, null);
            }
        }
        log.error("[AgentTaskConsumer] 任务缺少执行上下文且无法重建, taskId={}, triggerType={}",
                task.taskId(), task.triggerType());
        return null;
    }
}
