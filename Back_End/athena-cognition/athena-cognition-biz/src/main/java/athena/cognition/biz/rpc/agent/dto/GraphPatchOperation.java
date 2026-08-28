package athena.cognition.biz.rpc.agent.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Mirror of the Agent's GraphPatchOperation.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class GraphPatchOperation {

    public GraphOperationType operationType;
    public String targetId;
    public GraphNode node;
    public GraphEdge edge;
    public String supersededByNodeId;
    public List<String> evidenceIds = new ArrayList<>();
    public String reason;
}
