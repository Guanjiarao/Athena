package com.whu.software.athena.cognitionagent.intent.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class IntentClassificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void acceptsValidRequestAndReturnsContractResponse() throws Exception {
        mockMvc.perform(post("/internal/v1/cognition/nodes/intent-classification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRelatedRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.intent").value("RELATED"))
                .andExpect(jsonPath("$.evidenceClass").value("USER_PERSONAL_CLAIM"))
                .andExpect(jsonPath("$.nextRoute").value("MATCH_EXISTING_TOPIC_CANDIDATE"))
                .andExpect(jsonPath("$.policyResult").value("PASS"))
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    void returnsContractRejectionForInvalidBusinessInput() throws Exception {
        String request = validRelatedRequest().replace("\"selectedText\":\"经期前几天出现的情绪变化，需要继续观察。\"", "\"selectedText\":\"\"");

        mockMvc.perform(post("/internal/v1/cognition/nodes/intent-classification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.error.code").value("MISSING_REQUIRED_FIELD"))
                .andExpect(jsonPath("$.error.field").value("clue.selectedText"));
    }

    private String validRelatedRequest() {
        return """
                {
                  "contractVersion":"cognition-agent-v1",
                  "nodeVersion":"intent-evidence-v1",
                  "runId":"run_http_test_1",
                  "idempotencyKey":"clue_http_1:intent-evidence-v1",
                  "triggerType":"CLUE_CREATED",
                  "contextSnapshotId":"ctx_http_test_1",
                  "clue":{
                    "id":"clue_http_1",
                    "type":"ARTICLE_HIGHLIGHT",
                    "intent":"RELATED",
                    "relationType":"CURRENT",
                    "helpRequestType":"OBSERVE",
                    "articleId":"1024",
                    "articleTitle":"经期前情绪变化值得怎样记录",
                    "articleType":100,
                    "selectedText":"经期前几天出现的情绪变化，需要继续观察。",
                    "cycleRelation":"BEFORE_PERIOD",
                    "source":"KNOWLEDGE_ARTICLE",
                    "status":"PENDING",
                    "originalLabel":"和我有关",
                    "createdAt":"2026-08-23T10:00:00+08:00",
                    "updatedAt":"2026-08-23T10:00:00+08:00"
                  }
                }
                """;
    }
}
