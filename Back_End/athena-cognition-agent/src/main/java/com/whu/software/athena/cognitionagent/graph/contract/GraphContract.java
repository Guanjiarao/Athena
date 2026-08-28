package com.whu.software.athena.cognitionagent.graph.contract;

public final class GraphContract {

    public static final String CONTRACT_VERSION = "cognition-agent-v1";
    public static final String GRAPH_SCHEMA_VERSION = "personal-cognition-graph-v1";
    public static final String PROPOSAL_SCHEMA_VERSION = "graph-update-proposal-v1";
    public static final String WORKFLOW_VERSION = "cognition-graph-workflow-v1";

    public static final String EVIDENCE_NODE_ID = "EVIDENCE_CANONICALIZATION_AND_DEDUPLICATION";
    public static final String EVIDENCE_NODE_VERSION = "evidence-canonicalization-v1";
    public static final String TARGET_NODE_ID = "GRAPH_TARGET_RESOLUTION";
    public static final String TARGET_NODE_VERSION = "graph-target-resolution-v1";
    public static final String TARGET_PROMPT_VERSION = "graph-target-resolution-prompt-v1";
    public static final String SCOPE_NODE_ID = "GRAPH_UPDATE_SCOPE_PLANNING";
    public static final String SCOPE_NODE_VERSION = "graph-update-scope-v1";
    public static final String SEMANTIC_NODE_ID = "GRAPH_SEMANTIC_UPDATE_GENERATION";
    public static final String SEMANTIC_NODE_VERSION = "graph-semantic-update-v1";
    public static final String SEMANTIC_PROMPT_VERSION = "graph-semantic-update-prompt-v1";
    public static final String ACTION_NODE_ID = "NEXT_ACTION_PLANNING";
    public static final String ACTION_NODE_VERSION = "next-action-planning-v1";
    public static final String ACTION_PROMPT_VERSION = "next-action-planning-prompt-v1";
    public static final String PATCH_ASSEMBLY_NODE_ID = "GRAPH_PATCH_ASSEMBLY";
    public static final String PATCH_ASSEMBLY_NODE_VERSION = "graph-patch-assembly-v1";
    public static final String PATCH_GUARD_NODE_ID = "GRAPH_PATCH_GUARD";
    public static final String PATCH_GUARD_NODE_VERSION = "graph-patch-guard-v1";
    public static final String FEEDBACK_NORMALIZATION_NODE_ID =
            "ACTION_FEEDBACK_NORMALIZATION";
    public static final String FEEDBACK_NORMALIZATION_NODE_VERSION =
            "action-feedback-normalization-v1";
    public static final String FEEDBACK_GRAPH_NODE_ID =
            "ACTION_FEEDBACK_GRAPH_UPDATE";
    public static final String FEEDBACK_GRAPH_NODE_VERSION =
            "action-feedback-graph-update-v1";
    public static final String FEEDBACK_WORKFLOW_VERSION =
            "action-feedback-workflow-v1";

    private GraphContract() {
    }
}
