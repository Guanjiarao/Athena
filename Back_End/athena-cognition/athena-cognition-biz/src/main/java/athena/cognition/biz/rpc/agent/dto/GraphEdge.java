package athena.cognition.biz.rpc.agent.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Mirror of the Agent's GraphEdge.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class GraphEdge {

    public String id;
    public GraphEdgeType type;
    public String fromNodeId;
    public String toNodeId;
    public List<String> evidenceIds = new ArrayList<>();
    public boolean active = true;
    public String createdAt;
    public String updatedAt;
    public int version = 1;
}
