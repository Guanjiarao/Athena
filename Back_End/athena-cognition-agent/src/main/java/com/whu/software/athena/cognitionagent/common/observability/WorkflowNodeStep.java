package com.whu.software.athena.cognitionagent.common.observability;

public class WorkflowNodeStep {

    public String stepId;
    public String inputSummary;
    public String outputSummary;

    public WorkflowNodeStep() {
    }

    public WorkflowNodeStep(String stepId, String inputSummary, String outputSummary) {
        this.stepId = stepId;
        this.inputSummary = inputSummary;
        this.outputSummary = outputSummary;
    }
}
