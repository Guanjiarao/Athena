package com.whu.software.athena.cognitionagent.semantic;

import com.whu.software.athena.cognitionagent.graph.GraphTestFixtures;
import com.whu.software.athena.cognitionagent.graph.contract.GraphNodeType;
import com.whu.software.athena.cognitionagent.graph.contract.GraphUpdateRoute;
import com.whu.software.athena.cognitionagent.semantic.context.GraphSemanticContext;
import com.whu.software.athena.cognitionagent.semantic.contract.GraphSemanticUpdateDraft;
import com.whu.software.athena.cognitionagent.semantic.contract.SemanticChange;
import com.whu.software.athena.cognitionagent.semantic.contract.SemanticChangeType;
import com.whu.software.athena.cognitionagent.semantic.policy.GraphSemanticPolicyValidator;
import com.whu.software.athena.cognitionagent.scope.contract.GraphUpdateScope;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;

class GraphSemanticPolicyValidatorTest {

    @Test
    void articleRelevanceCannotBecomeConfirmedBodyFact() {
        GraphUpdateScope scope = new GraphUpdateScope();
        scope.graphId = "graph_1";
        scope.route = GraphUpdateRoute.CREATE_BRANCH;
        scope.proposedTopicTitle = "经前情绪变化";
        scope.selectedEvidenceIds = List.of("evidence_1");
        GraphSemanticContext context = new GraphSemanticContext(
                GraphTestFixtures.emptyGraph(), scope,
                List.of(GraphTestFixtures.declaredEvidence("evidence_1")), List.of());

        SemanticChange change = new SemanticChange();
        change.changeType = SemanticChangeType.ADD;
        change.nodeType = GraphNodeType.SELF_REPORTED_FACT;
        change.content = "用户已经出现经前情绪变化";
        change.evidenceIds = List.of("evidence_1");
        GraphSemanticUpdateDraft draft = new GraphSemanticUpdateDraft();
        draft.topicTitle = "经前情绪变化";
        draft.stageUnderstanding = "仍需继续观察。";
        draft.stageUnderstandingEvidenceIds = List.of("evidence_1");
        draft.changes = List.of(change);
        draft.changeSummary = "新增事实";

        assertFalse(new GraphSemanticPolicyValidator().validate(context, draft).allowed());
    }
}
