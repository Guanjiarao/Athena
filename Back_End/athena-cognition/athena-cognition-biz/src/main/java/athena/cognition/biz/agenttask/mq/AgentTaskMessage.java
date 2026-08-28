package athena.cognition.biz.agenttask.mq;

/**
 * MQ body of an agent task dispatch. Deliberately minimal: the task row
 * (cognition_agent_task.payload_json) is the source of truth for the execution
 * context, so a redelivery by the crash-recovery sweeper needs nothing else.
 */
public record AgentTaskMessage(String taskId, String triggerType) {
}
