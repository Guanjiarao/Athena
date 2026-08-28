package com.whu.software.athena.cognitionagent.graph.contract;

import java.util.ArrayList;
import java.util.List;

public class GraphNode {

    public String id;
    public GraphNodeType type;
    public GraphNodeStatus status = GraphNodeStatus.ACTIVE;
    public String topicId;
    public String title;
    public String content;
    public String domain;
    public List<String> evidenceIds = new ArrayList<>();
    public GraphActionType actionType;
    public GraphActionStatus actionStatus;
    public String dueAt;
    public List<GraphActionFeedbackResult> feedbackOptions = new ArrayList<>();
    public String createdAt;
    public String updatedAt;
    public int version = 1;
}
