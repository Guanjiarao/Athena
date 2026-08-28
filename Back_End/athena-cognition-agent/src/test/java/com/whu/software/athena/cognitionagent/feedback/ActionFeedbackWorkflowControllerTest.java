package com.whu.software.athena.cognitionagent.feedback;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.whu.software.athena.cognitionagent.graph.contract.GraphActionFeedbackResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ActionFeedbackWorkflowControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper mapper;

    @Test
    void synchronousEndpointReturnsPreviewAndPublishesFeedbackMetrics() throws Exception {
        var request = ActionFeedbackTestFixtures.request(
                "http", GraphActionFeedbackResult.OCCURRED);

        mockMvc.perform(post("/internal/v1/cognition/workflows/action-feedback/prepare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROPOSAL_READY"))
                .andExpect(jsonPath("$.nextNodeId").value("HUMAN_CONFIRMATION"))
                .andExpect(jsonPath("$.proposal.status")
                        .value("READY_FOR_CONFIRMATION"))
                .andExpect(jsonPath("$.graphPreview.graphVersion").value(4))
                .andExpect(jsonPath("$.normalizationResult.observation.feedbackResult")
                        .value("OCCURRED"))
                .andExpect(jsonPath("$.graphUpdateResult.observation.operationCount")
                        .isNumber())
                .andExpect(jsonPath("$.observation.nodeId")
                        .value("ACTION_FEEDBACK_WORKFLOW"));

        mockMvc.perform(get("/actuator/metrics/athena.cognition.graph.node.runs")
                        .param("tag", "node:ACTION_FEEDBACK_NORMALIZATION"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.measurements[0].value").isNumber());

        mockMvc.perform(get("/actuator/metrics/athena.cognition.graph.patch.operations")
                        .param("tag", "node:ACTION_FEEDBACK_GRAPH_UPDATE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.measurements[0].value").isNumber());

        mockMvc.perform(get("/actuator/metrics/athena.cognition.workflow.runs")
                        .param("tag", "workflow:action-feedback-workflow-v1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.measurements[0].value").isNumber());
    }
}
