package com.whu.software.athena.cognitionagent.action.context;

import com.whu.software.athena.cognitionagent.graph.contract.EvidenceFactLevel;
import com.whu.software.athena.cognitionagent.graph.contract.EvidenceSourceType;

public record ActionEvidenceView(
        String evidenceId,
        EvidenceSourceType sourceType,
        EvidenceFactLevel factLevel,
        String summary
) {
}
