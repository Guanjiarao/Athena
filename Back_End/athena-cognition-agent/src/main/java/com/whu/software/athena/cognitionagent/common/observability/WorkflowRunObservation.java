package com.whu.software.athena.cognitionagent.common.observability;

import com.whu.software.athena.cognitionagent.intent.contract.ModelCallStatus;
import com.whu.software.athena.cognitionagent.intent.contract.PolicyResult;
import com.whu.software.athena.cognitionagent.intent.contract.SchemaResult;

import java.util.ArrayList;
import java.util.List;

public class WorkflowRunObservation {

    public String runId;
    public String triggerType;
    public String workflowVersion;
    public String nodeId;
    public String nodeVersion;
    public String promptVersion;
    public String modelProvider;
    public String modelName;
    public String contextSnapshotId;
    public List<WorkflowNodeStep> steps = new ArrayList<>();
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
    public String feedbackResult;
    public Integer operationCount;
    public Long baseGraphVersion;
    public Long previewGraphVersion;
}
