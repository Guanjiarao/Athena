package com.whu.software.athena.cognitionagent.semantic.context;

import com.whu.software.athena.cognitionagent.graph.contract.EvidenceFactLevel;
import com.whu.software.athena.cognitionagent.graph.contract.EvidenceSourceType;
import com.whu.software.athena.cognitionagent.intent.contract.CycleRelation;

public record SemanticEvidenceView(
        String evidenceId,
        EvidenceSourceType sourceType,
        EvidenceFactLevel factLevel,
        String summary,
        String occurredAt,
        CycleRelation cycleRelation,
        Integer severity,
        Boolean resolved
) {
}
