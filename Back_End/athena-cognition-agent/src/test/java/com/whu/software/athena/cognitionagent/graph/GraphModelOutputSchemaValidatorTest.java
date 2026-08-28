package com.whu.software.athena.cognitionagent.graph;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.whu.software.athena.cognitionagent.semantic.schema.SemanticModelOutputSchemaValidator;
import com.whu.software.athena.cognitionagent.target.schema.TargetModelOutputSchemaValidator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

class GraphModelOutputSchemaValidatorTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void targetOutputRejectsDatabaseActionInjection() {
        ObjectNode output = mapper.createObjectNode();
        output.put("route", "CREATE_BRANCH");
        output.putNull("matchedTopicId");
        output.put("suggestedTopicTitle", "周期变化");
        output.put("rationale", "new branch");
        output.put("databaseAction", "INSERT");

        assertFalse(new TargetModelOutputSchemaValidator().validate(output).valid());
    }

    @Test
    void semanticOutputRequiresStageEvidenceAndRejectsExtraDiagnosis() {
        ObjectNode output = mapper.createObjectNode();
        output.put("topicTitle", "周期变化");
        output.put("stageUnderstanding", "仍需观察。");
        output.putArray("changes");
        output.put("changeSummary", "无新增");
        output.put("diagnosis", "forbidden");

        assertFalse(new SemanticModelOutputSchemaValidator().validate(output).valid());
    }
}
