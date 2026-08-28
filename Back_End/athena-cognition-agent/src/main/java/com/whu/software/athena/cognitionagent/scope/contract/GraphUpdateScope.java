package com.whu.software.athena.cognitionagent.scope.contract;

import com.whu.software.athena.cognitionagent.graph.contract.GraphUpdateRoute;

import java.util.ArrayList;
import java.util.List;

public class GraphUpdateScope {

    public String graphId;
    public long baseGraphVersion;
    public GraphUpdateRoute route;
    public String targetTopicId;
    public String proposedTopicTitle;
    public List<String> selectedEvidenceIds = new ArrayList<>();
    public List<String> readableNodeIds = new ArrayList<>();
    public List<String> mutableNodeIds = new ArrayList<>();
    public List<String> immutableNodeIds = new ArrayList<>();
}
