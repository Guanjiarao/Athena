package com.whu.software.athena.cognitionagent.evidence.context;

import com.whu.software.athena.cognitionagent.evidence.contract.EvidenceCandidate;
import com.whu.software.athena.cognitionagent.graph.contract.CanonicalEvidence;

import java.util.List;

/** Node-internal allow-list. This node has no model-visible context. */
public record EvidenceCanonicalizationContext(
        List<EvidenceCandidate> candidates,
        List<CanonicalEvidence> existingEvidence
) {
}
