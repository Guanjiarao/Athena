package com.whu.software.athena.cognitionagent.target.context;

import com.whu.software.athena.cognitionagent.graph.contract.EvidenceFactLevel;
import com.whu.software.athena.cognitionagent.graph.contract.EvidenceSourceType;

public record TargetEvidenceModelView(
        String evidenceId,
        EvidenceSourceType sourceType,
        EvidenceFactLevel factLevel,
        String summary
) {
}
