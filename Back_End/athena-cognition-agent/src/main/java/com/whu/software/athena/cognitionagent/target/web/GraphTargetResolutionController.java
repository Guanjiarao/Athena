package com.whu.software.athena.cognitionagent.target.web;

import com.whu.software.athena.cognitionagent.target.contract.GraphTargetResolutionRequest;
import com.whu.software.athena.cognitionagent.target.contract.GraphTargetResolutionResponse;
import com.whu.software.athena.cognitionagent.target.service.GraphTargetResolutionService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/cognition/nodes/graph-target-resolution")
public class GraphTargetResolutionController {

    private final GraphTargetResolutionService service;

    public GraphTargetResolutionController(GraphTargetResolutionService service) {
        this.service = service;
    }

    @PostMapping
    public GraphTargetResolutionResponse resolve(
            @RequestBody GraphTargetResolutionRequest request) {
        return service.resolve(request);
    }
}
