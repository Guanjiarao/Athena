package com.whu.software.athena.cognitionagent.evidence.context;

import com.whu.software.athena.cognitionagent.evidence.contract.EvidenceCanonicalizationRequest;

import java.util.List;

public class EvidenceCanonicalizationContextBuilder {

    public EvidenceCanonicalizationContext build(EvidenceCanonicalizationRequest request) {
        return new EvidenceCanonicalizationContext(
                request.candidates == null ? List.of() : List.copyOf(request.candidates),
                request.existingEvidence == null ? List.of() : List.copyOf(request.existingEvidence));
    }
}
