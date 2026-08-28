package com.whu.software.athena.cognitionagent.graph.contract;

import java.util.ArrayList;
import java.util.List;

/**
 * The only object that may later be applied by the main backend after user
 * confirmation. Workflow nodes may prepare it but cannot apply it.
 */
public class GraphUpdateProposal {

    public String proposalSchemaVersion = GraphContract.PROPOSAL_SCHEMA_VERSION;
    public String proposalId;
    public String graphId;
    public long baseGraphVersion;
    public GraphProposalStatus status = GraphProposalStatus.DRAFT;
    public GraphUpdateRoute route;
    public String targetTopicId;
    public List<String> evidenceIds = new ArrayList<>();
    public List<GraphPatchOperation> operations = new ArrayList<>();
    public String changeSummary;
    public boolean requiresUserConfirmation = true;
    public String workflowVersion = GraphContract.WORKFLOW_VERSION;
    public String createdAt;
}
