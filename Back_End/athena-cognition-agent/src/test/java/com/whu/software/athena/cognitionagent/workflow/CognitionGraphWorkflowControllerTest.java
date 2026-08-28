package com.whu.software.athena.cognitionagent.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.whu.software.athena.cognitionagent.graph.GraphTestFixtures;
import com.whu.software.athena.cognitionagent.graph.contract.GraphTriggerType;
import com.whu.software.athena.cognitionagent.workflow.contract.GraphUpdatePreparationRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CognitionGraphWorkflowControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper mapper;

    @Test
    void synchronousWorkflowReturnsGuardedProposal() throws Exception {
        GraphUpdatePreparationRequest request = new GraphUpdatePreparationRequest();
        request.runId = "run_http_1";
        request.idempotencyKey = "clue_http_1:graph-workflow-v1";
        request.triggerType = GraphTriggerType.USER_REQUEST;
        request.contextSnapshotId = "ctx_http_1";
        request.graph = GraphTestFixtures.emptyGraph();
        request.candidates.add(
                GraphTestFixtures.relatedCandidate("evidence_http_1", "clue_http_1"));
        request.suggestedTopicTitle = "经前情绪变化";

        mockMvc.perform(post("/internal/v1/cognition/workflows/graph-update/prepare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROPOSAL_READY"))
                .andExpect(jsonPath("$.nextNodeId").value("HUMAN_CONFIRMATION"))
                .andExpect(jsonPath("$.proposal.status").value("READY_FOR_CONFIRMATION"))
                .andExpect(jsonPath("$.proposal.requiresUserConfirmation").value(true))
                .andExpect(jsonPath("$.patchGuardResult.policyResult").value("PASS"))
                .andExpect(jsonPath("$.graphPreview.graphVersion").value(1))
                .andExpect(jsonPath("$.observation.nodeId")
                        .value("COGNITION_GRAPH_WORKFLOW"));

        mockMvc.perform(get("/actuator/metrics/athena.cognition.workflow.runs")
                        .param("tag", "workflow:cognition-graph-workflow-v1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.measurements[0].value").isNumber());
    }

    @Test
    void graphWorkflowRejectsFeedbackTriggerAtTheBoundary() throws Exception {
        GraphUpdatePreparationRequest request = new GraphUpdatePreparationRequest();
        request.runId = "run_wrong_route";
        request.idempotencyKey = "feedback_wrong_route";
        request.triggerType = GraphTriggerType.ACTION_FEEDBACK;
        request.contextSnapshotId = "ctx_wrong_route";
        request.graph = GraphTestFixtures.emptyGraph();
        request.candidates.add(GraphTestFixtures.relatedCandidate(
                "evidence_wrong_route", "clue_wrong_route"));

        mockMvc.perform(post("/internal/v1/cognition/workflows/graph-update/prepare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.error.field").value("triggerType"))
                .andExpect(jsonPath("$.observation.schemaResult").value("FAIL"));
    }
}
