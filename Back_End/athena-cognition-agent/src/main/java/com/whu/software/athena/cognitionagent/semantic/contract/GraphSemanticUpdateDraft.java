package com.whu.software.athena.cognitionagent.semantic.contract;

import java.util.ArrayList;
import java.util.List;

public class GraphSemanticUpdateDraft {

    public String topicTitle;
    public String stageUnderstanding;
    public List<String> stageUnderstandingEvidenceIds = new ArrayList<>();
    public List<SemanticChange> changes = new ArrayList<>();
    public String changeSummary;
}
