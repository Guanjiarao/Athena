package com.whu.software.athena.cognitionagent.intent.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class IntentMetricsEndpointTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void formalRequestPublishesRunAndDurationMetrics() throws Exception {
        mockMvc.perform(post("/internal/v1/cognition/nodes/intent-classification")
                        .contentType(APPLICATION_JSON)
                        .content(validQuestionRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.observation.modelCallStatus").value("SUCCEEDED"))
                .andExpect(jsonPath("$.observation.schemaResult").value("PASS"));

        mockMvc.perform(get("/actuator/metrics/athena.agent.node.runs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("athena.agent.node.runs"))
                .andExpect(jsonPath("$.measurements[0].value").isNumber());

        mockMvc.perform(get("/actuator/metrics/athena.agent.node.duration"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("athena.agent.node.duration"));
    }

    private String validQuestionRequest() {
        return """
                {
                  "contractVersion":"cognition-agent-v1",
                  "nodeVersion":"intent-evidence-v1",
                  "runId":"run_metrics_test_1",
                  "idempotencyKey":"clue_metrics_1:intent-evidence-v1",
                  "triggerType":"CLUE_CREATED",
                  "contextSnapshotId":"ctx_metrics_test_1",
                  "clue":{
                    "id":"clue_metrics_1",
                    "type":"ARTICLE_HIGHLIGHT",
                    "intent":"QUESTION",
                    "helpRequestType":"KNOWLEDGE",
                    "articleId":"article_1",
                    "articleTitle":"Cycle changes",
                    "articleType":100,
                    "selectedText":"Selected article text",
                    "questionType":"IS_COMMON",
                    "questionText":"Is this common?",
                    "cycleRelation":"NO_RELATION",
                    "source":"KNOWLEDGE_ARTICLE",
                    "status":"PENDING",
                    "originalLabel":"I have a question",
                    "createdAt":"2026-08-26T10:00:00+08:00",
                    "updatedAt":"2026-08-26T10:00:00+08:00"
                  }
                }
                """;
    }
}
