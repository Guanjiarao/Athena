package com.whu.software.athena.cognitionagent.graph.contract;

import java.util.ArrayList;
import java.util.List;

public class GraphPatchOperation {

    public GraphOperationType operationType;
    public String targetId;
    public GraphNode node;
    public GraphEdge edge;
    public String supersededByNodeId;
    public List<String> evidenceIds = new ArrayList<>();
    public String reason;
}
