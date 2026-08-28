package com.whu.software.athena.cognitionagent.intent.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.http.MediaType.APPLICATION_JSON;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("model-probe")
class IntentModelProbeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void localProbeReturnsProviderSuggestionWithoutBusinessMutation() throws Exception {
        mockMvc.perform(post("/internal/v1/cognition/nodes/intent-classification/model-probe")
                        .contentType(APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROVIDER_SUCCEEDED"))
                .andExpect(jsonPath("$.suggestion.provider").value("mock"))
                .andExpect(jsonPath("$.suggestion.suggestedIntent").value("QUESTION"))
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    private String validRequest() {
        return """
                {
                  "contractVersion":"cognition-agent-v1",
                  "nodeVersion":"intent-evidence-v1",
                  "runId":"run_probe_test_1",
                  "idempotencyKey":"clue_probe_1:intent-evidence-v1",
                  "triggerType":"CLUE_CREATED",
                  "contextSnapshotId":"ctx_probe_test_1",
                  "clue":{
                    "id":"clue_probe_1",
                    "type":"ARTICLE_HIGHLIGHT",
                    "intent":"QUESTION",
                    "helpRequestType":"KNOWLEDGE",
                    "articleId":"1024",
                    "articleTitle":"经期前情绪变化值得怎样记录",
                    "articleType":100,
                    "selectedText":"经期前几天出现的情绪变化，需要继续观察。",
                    "questionType":"IS_COMMON",
                    "questionText":"这是否常见？",
                    "cycleRelation":"NO_RELATION",
                    "source":"KNOWLEDGE_ARTICLE",
                    "status":"PENDING",
                    "originalLabel":"我有疑问",
                    "createdAt":"2026-08-23T10:00:00+08:00",
                    "updatedAt":"2026-08-23T10:00:00+08:00"
                  }
                }
                """;
    }
}
