package com.whu.software.athena.cognitionagent.graph;

import com.whu.software.athena.cognitionagent.evidence.contract.EvidenceCandidate;
import com.whu.software.athena.cognitionagent.graph.contract.CanonicalEvidence;
import com.whu.software.athena.cognitionagent.graph.contract.EvidenceFactLevel;
import com.whu.software.athena.cognitionagent.graph.contract.EvidenceSourceType;
import com.whu.software.athena.cognitionagent.graph.contract.GraphNode;
import com.whu.software.athena.cognitionagent.graph.contract.GraphNodeStatus;
import com.whu.software.athena.cognitionagent.graph.contract.GraphNodeType;
import com.whu.software.athena.cognitionagent.graph.contract.PersonalCognitionGraph;
import com.whu.software.athena.cognitionagent.intent.contract.ClueIntent;
import com.whu.software.athena.cognitionagent.intent.contract.CycleRelation;
import com.whu.software.athena.cognitionagent.intent.contract.RelationType;

import java.util.List;

public final class GraphTestFixtures {

    private GraphTestFixtures() {
    }

    public static PersonalCognitionGraph graphWithTwoTopics() {
        PersonalCognitionGraph graph = emptyGraph();
        graph.graphVersion = 4;
        graph.nodes.add(topic("topic_mood", "经前情绪变化", "MOOD"));
        graph.nodes.add(hypothesis("hyp_mood", "topic_mood",
                "目前只看到一次时间上的联系。"));
        graph.nodes.add(topic("topic_sleep", "睡眠状态", "SLEEP"));
        graph.nodes.add(hypothesis("hyp_sleep", "topic_sleep",
                "最近睡眠记录仍然不足。"));
        return graph;
    }

    public static PersonalCognitionGraph emptyGraph() {
        PersonalCognitionGraph graph = new PersonalCognitionGraph();
        graph.graphId = "graph_1";
        graph.graphVersion = 0;
        graph.updatedAt = "2026-08-27T08:00:00+08:00";
        return graph;
    }

    public static EvidenceCandidate relatedCandidate(String evidenceId, String sourceId) {
        EvidenceCandidate value = new EvidenceCandidate();
        value.evidenceId = evidenceId;
        value.sourceType = EvidenceSourceType.ARTICLE_HIGHLIGHT;
        value.sourceId = sourceId;
        value.intent = ClueIntent.RELATED;
        value.relationType = RelationType.OBSERVE;
        value.summary = "经期前几天出现的情绪变化，需要继续观察是否重复。";
        value.occurredAt = "2026-08-27T08:00:00+08:00";
        value.cycleRelation = CycleRelation.BEFORE_PERIOD;
        return value;
    }

    public static CanonicalEvidence declaredEvidence(String evidenceId) {
        CanonicalEvidence value = new CanonicalEvidence();
        value.evidenceId = evidenceId;
        value.sourceType = EvidenceSourceType.ARTICLE_HIGHLIGHT;
        value.sourceId = "clue_" + evidenceId;
        value.factLevel = EvidenceFactLevel.DECLARED_RELEVANCE;
        value.summary = "经期前几天出现的情绪变化，需要继续观察是否重复。";
        value.contentFingerprint = "fingerprint_" + evidenceId;
        value.cycleRelation = CycleRelation.BEFORE_PERIOD;
        return value;
    }

    private static GraphNode topic(String id, String title, String domain) {
        GraphNode node = new GraphNode();
        node.id = id;
        node.type = GraphNodeType.TOPIC;
        node.status = GraphNodeStatus.ACTIVE;
        node.title = title;
        node.content = "正在观察";
        node.domain = domain;
        node.createdAt = "2026-08-20T08:00:00+08:00";
        node.updatedAt = node.createdAt;
        return node;
    }

    private static GraphNode hypothesis(String id, String topicId, String content) {
        GraphNode node = new GraphNode();
        node.id = id;
        node.type = GraphNodeType.PATTERN_HYPOTHESIS;
        node.status = GraphNodeStatus.ACTIVE;
        node.topicId = topicId;
        node.content = content;
        node.evidenceIds = List.of("old_" + id);
        node.createdAt = "2026-08-20T08:00:00+08:00";
        node.updatedAt = node.createdAt;
        return node;
    }
}
