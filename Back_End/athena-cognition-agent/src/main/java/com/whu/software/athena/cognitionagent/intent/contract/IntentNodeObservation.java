package com.whu.software.athena.cognitionagent.intent.contract;

/** Redacted input/output summaries for one node execution. */
public class IntentNodeObservation {

    public String nodeId;
    public String inputSummary;
    public String outputSummary;

    public IntentNodeObservation() {
    }

    public IntentNodeObservation(String nodeId, String inputSummary, String outputSummary) {
        this.nodeId = nodeId;
        this.inputSummary = inputSummary;
        this.outputSummary = outputSummary;
    }
}
