package com.whu.software.athena.cognitionagent.common.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class WorkflowTelemetryRecorder {

    private static final Logger LOGGER = LoggerFactory.getLogger(WorkflowTelemetryRecorder.class);
    private final MeterRegistry registry;

    public WorkflowTelemetryRecorder(MeterRegistry registry) {
        this.registry = registry;
    }

    public void record(String status, WorkflowRunObservation observation) {
        if (observation == null) {
            return;
        }
        Counter.builder("athena.cognition.graph.node.runs")
                .tag("node", safe(observation.nodeId))
                .tag("status", safe(status))
                .tag("model_status", enumName(observation.modelCallStatus))
                .tag("schema_result", enumName(observation.schemaResult))
                .tag("policy_result", enumName(observation.policyResult))
                .tag("feedback_result", safe(observation.feedbackResult))
                .register(registry)
                .increment();
        Timer.builder("athena.cognition.graph.node.duration")
                .tag("node", safe(observation.nodeId))
                .tag("status", safe(status))
                .register(registry)
                .record(Duration.ofMillis(Math.max(0L,
                        observation.latencyMs == null ? 0L : observation.latencyMs)));
        if (observation.operationCount != null) {
            DistributionSummary.builder("athena.cognition.graph.patch.operations")
                    .tag("node", safe(observation.nodeId))
                    .tag("status", safe(status))
                    .register(registry)
                    .record(Math.max(0, observation.operationCount));
        }
        LOGGER.info("graph_node_run runId={} triggerType={} workflowVersion={} nodeId={} "
                        + "nodeVersion={} promptVersion={} modelProvider={} modelName={} "
                        + "contextSnapshotId={} status={} modelStatus={} schemaResult={} "
                        + "policyResult={} modelPolicyResult={} latencyMs={} inputTokens={} "
                        + "outputTokens={} totalTokens={} estimatedCost={} retryCount={} "
                        + "modelErrorCode={} evidenceIds={} feedbackResult={} operationCount={} "
                        + "baseGraphVersion={} previewGraphVersion={}",
                observation.runId, observation.triggerType, observation.workflowVersion,
                observation.nodeId, observation.nodeVersion, observation.promptVersion,
                observation.modelProvider, observation.modelName, observation.contextSnapshotId,
                safe(status), observation.modelCallStatus, observation.schemaResult,
                observation.policyResult, observation.modelPolicyResult, observation.latencyMs,
                observation.inputTokens, observation.outputTokens, observation.totalTokens,
                observation.estimatedCost, observation.retryCount, observation.modelErrorCode,
                observation.evidenceIds, observation.feedbackResult,
                observation.operationCount, observation.baseGraphVersion,
                observation.previewGraphVersion);
    }

    public void recordWorkflow(String workflow,
                               String status,
                               WorkflowRunObservation observation) {
        if (observation == null) return;
        String triggerType = safe(observation.triggerType);
        Counter.builder("athena.cognition.workflow.runs")
                .tag("workflow", safe(workflow))
                .tag("status", safe(status))
                .tag("trigger_type", triggerType)
                .register(registry)
                .increment();
        Timer.builder("athena.cognition.workflow.duration")
                .tag("workflow", safe(workflow))
                .tag("status", safe(status))
                .tag("trigger_type", triggerType)
                .register(registry)
                .record(Duration.ofMillis(Math.max(0L,
                        observation.latencyMs == null ? 0L : observation.latencyMs)));
        LOGGER.info("graph_workflow_run runId={} workflow={} triggerType={} status={} "
                        + "contextSnapshotId={} latencyMs={} evidenceIds={} operationCount={} "
                        + "baseGraphVersion={} previewGraphVersion={}",
                observation.runId, safe(workflow), observation.triggerType, safe(status),
                observation.contextSnapshotId, observation.latencyMs,
                observation.evidenceIds, observation.operationCount,
                observation.baseGraphVersion, observation.previewGraphVersion);
    }

    private String enumName(Enum<?> value) {
        return value == null ? "NOT_RUN" : value.name();
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "UNKNOWN" : value;
    }
}
