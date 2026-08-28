package com.whu.software.athena.cognitionagent.intent.contract;

import java.util.ArrayList;
import java.util.List;

/**
 * Non-sensitive execution record returned by the local node for later persistence
 * by the Athena main backend.
 */
public class IntentRunObservation {

    public String runId;
    public TriggerType triggerType;
    /** The intent explicitly selected by the user for this clue. */
    public ClueIntent userDecision;
    public String workflowVersion;
    public String nodeVersion;
    public String promptVersion;
    public String modelProvider;
    public String modelName;
    public String contextSnapshotId;
    public List<IntentNodeObservation> nodes = new ArrayList<>();
    public List<String> evidenceIds = new ArrayList<>();
    public Long latencyMs;
    public Integer inputTokens;
    public Integer outputTokens;
    public Integer totalTokens;
    public Double estimatedCost;
    public Integer retryCount = 0;
    public SchemaResult schemaResult = SchemaResult.NOT_RUN;
    public PolicyResult policyResult;
    public PolicyResult modelPolicyResult;
    public ModelCallStatus modelCallStatus = ModelCallStatus.NOT_ATTEMPTED;
    public String modelErrorCode;
    public Boolean modelConflict = false;
}
