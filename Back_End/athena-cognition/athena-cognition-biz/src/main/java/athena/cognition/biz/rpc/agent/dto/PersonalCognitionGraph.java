package athena.cognition.biz.rpc.agent.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Mirror of the Agent's PersonalCognitionGraph: one versioned cognition graph
 * per user. The user identity stays outside the Agent contract and is owned by
 * this main backend.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PersonalCognitionGraph {

    public String graphSchemaVersion = GraphContract.GRAPH_SCHEMA_VERSION;
    public String graphId;
    public long graphVersion;
    public List<GraphNode> nodes = new ArrayList<>();
    public List<GraphEdge> edges = new ArrayList<>();
    public String updatedAt;
}
