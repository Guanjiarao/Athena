package com.whu.software.athena.cognitionagent.intent.observability;

import com.whu.software.athena.cognitionagent.intent.contract.IntentClassificationResponse;
import com.whu.software.athena.cognitionagent.intent.contract.IntentRunObservation;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Emits low-cardinality metrics and one redacted structured log per run. */
@Component
public class IntentTelemetryRecorder {

    private static final Logger LOGGER = LoggerFactory.getLogger(IntentTelemetryRecorder.class);
    private final MeterRegistry meterRegistry;

    public IntentTelemetryRecorder(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void record(IntentClassificationResponse response) {
        if (response == null || response.observation == null) {
            return;
        }
        IntentRunObservation observation = response.observation;
        String status = enumName(response.status);
        String modelStatus = enumName(observation.modelCallStatus);
        String schemaStatus = enumName(observation.schemaResult);
        String policyStatus = enumName(observation.policyResult);

        Counter.builder("athena.agent.node.runs")
                .tag("node", observation.nodeVersion)
                .tag("status", status)
                .tag("model_status", modelStatus)
                .tag("schema_result", schemaStatus)
                .tag("policy_result", policyStatus)
                .register(meterRegistry)
                .increment();
        Timer.builder("athena.agent.node.duration")
                .tag("node", observation.nodeVersion)
                .tag("status", status)
                .register(meterRegistry)
                .record(Duration.ofMillis(Math.max(0L, observation.latencyMs == null ? 0L : observation.latencyMs)));

        LOGGER.info("agent_run runId={} triggerType={} userDecision={} workflowVersion={} nodeVersion={} "
                        + "promptVersion={} modelProvider={} modelName={} contextSnapshotId={} "
                        + "status={} modelStatus={} schemaResult={} policyResult={} modelPolicyResult={} "
                        + "modelConflict={} latencyMs={} inputTokens={} outputTokens={} totalTokens={} "
                        + "estimatedCost={} retryCount={} modelErrorCode={} evidenceIds={}",
                observation.runId,
                observation.triggerType,
                observation.userDecision,
                observation.workflowVersion,
                observation.nodeVersion,
                observation.promptVersion,
                observation.modelProvider,
                observation.modelName,
                observation.contextSnapshotId,
                status,
                modelStatus,
                schemaStatus,
                policyStatus,
                enumName(observation.modelPolicyResult),
                observation.modelConflict,
                observation.latencyMs,
                observation.inputTokens,
                observation.outputTokens,
                observation.totalTokens,
                observation.estimatedCost,
                observation.retryCount,
                observation.modelErrorCode,
                observation.evidenceIds);
    }

    private String enumName(Enum<?> value) {
        return value == null ? "NOT_RUN" : value.name();
    }
}
