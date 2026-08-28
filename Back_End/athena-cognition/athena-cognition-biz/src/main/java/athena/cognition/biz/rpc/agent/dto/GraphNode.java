package athena.cognition.biz.rpc.agent.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Mirror of the Agent's GraphNode.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
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
