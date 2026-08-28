package com.whu.software.athena.cognitionagent.action.contract;

import com.whu.software.athena.cognitionagent.graph.contract.GraphActionFeedbackResult;
import com.whu.software.athena.cognitionagent.graph.contract.GraphActionType;

import java.util.ArrayList;
import java.util.List;

public class NextActionPlan {

    public ActionPlanningDecision decision;
    public String existingActionNodeId;
    public GraphActionType actionType;
    public String title;
    public String description;
    public String dueAt;
    public List<GraphActionFeedbackResult> feedbackOptions = new ArrayList<>();
    public List<String> evidenceIds = new ArrayList<>();
    public String rationale;
}
