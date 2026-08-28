package com.whu.software.athena.cognitionagent.evidence.web;

import com.whu.software.athena.cognitionagent.evidence.contract.EvidenceCanonicalizationRequest;
import com.whu.software.athena.cognitionagent.evidence.contract.EvidenceCanonicalizationResponse;
import com.whu.software.athena.cognitionagent.evidence.service.EvidenceCanonicalizationService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/cognition/nodes/evidence-canonicalization")
public class EvidenceCanonicalizationController {

    private final EvidenceCanonicalizationService service;

    public EvidenceCanonicalizationController(EvidenceCanonicalizationService service) {
        this.service = service;
    }

    @PostMapping
    public EvidenceCanonicalizationResponse canonicalize(
            @RequestBody EvidenceCanonicalizationRequest request) {
        return service.canonicalize(request);
    }
}
