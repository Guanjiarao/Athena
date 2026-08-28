package com.whu.software.athena.cognitionagent.action.provider;

import com.whu.software.athena.cognitionagent.graph.contract.GraphActionType;

import java.util.ArrayList;
import java.util.List;

public class NextActionModelOutput {

    public GraphActionType actionType;
    public String title;
    public String description;
    public List<String> evidenceIds = new ArrayList<>();
    public String rationale;
}
