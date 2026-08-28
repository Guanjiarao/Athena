package com.whu.software.athena.cognitionagent.graph.contract;

import java.util.ArrayList;
import java.util.List;

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
