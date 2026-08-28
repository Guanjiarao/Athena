package com.whu.software.athena.cognitionagent.intent.contract;

import java.util.ArrayList;
import java.util.List;

public class IntentClassificationResponse {

    public String contractVersion;
    public String nodeVersion;
    public String runId;
    public String clueId;
    public String nodeId;
    public IntentClassificationStatus status;
    public ClueIntent intent;
    public EvidenceClass evidenceClass;
    public FactEligibility factEligibility;
    public DecisionSource decisionSource;
    public AmbiguityCode ambiguityCode;
    public NextRoute nextRoute;
    public List<String> evidenceIds = new ArrayList<>();
    public PolicyResult policyResult;
    public SchemaResult schemaResult;
    public IntentModelSuggestionView modelSuggestion;
    public boolean modelConflict;
    public IntentRunObservation observation;
    public AgentError error;
}
