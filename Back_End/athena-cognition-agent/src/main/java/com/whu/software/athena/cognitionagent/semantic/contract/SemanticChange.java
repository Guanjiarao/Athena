package com.whu.software.athena.cognitionagent.semantic.contract;

import com.whu.software.athena.cognitionagent.graph.contract.GraphNodeType;

import java.util.ArrayList;
import java.util.List;

public class SemanticChange {

    public SemanticChangeType changeType;
    public GraphNodeType nodeType;
    public String targetNodeId;
    public String content;
    public List<String> evidenceIds = new ArrayList<>();
}
