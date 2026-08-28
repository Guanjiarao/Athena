package com.whu.software.athena.cognitionagent.graph.contract;

import java.util.ArrayList;
import java.util.List;

/**
 * One versioned cognition graph per user. The user identity remains outside the
 * Agent contract and is owned by the Athena main backend.
 */
public class PersonalCognitionGraph {

    public String graphSchemaVersion = GraphContract.GRAPH_SCHEMA_VERSION;
    public String graphId;
    public long graphVersion;
    public List<GraphNode> nodes = new ArrayList<>();
    public List<GraphEdge> edges = new ArrayList<>();
    public String updatedAt;
}
