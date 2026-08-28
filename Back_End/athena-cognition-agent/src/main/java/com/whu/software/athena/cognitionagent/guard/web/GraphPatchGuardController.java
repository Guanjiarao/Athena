package com.whu.software.athena.cognitionagent.guard.web;

import com.whu.software.athena.cognitionagent.guard.contract.GraphPatchGuardRequest;
import com.whu.software.athena.cognitionagent.guard.contract.GraphPatchGuardResponse;
import com.whu.software.athena.cognitionagent.guard.service.GraphPatchGuardService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/cognition/nodes/graph-patch-guard")
public class GraphPatchGuardController {

    private final GraphPatchGuardService service;

    public GraphPatchGuardController(GraphPatchGuardService service) {
        this.service = service;
    }

    @PostMapping
    public GraphPatchGuardResponse guard(@RequestBody GraphPatchGuardRequest request) {
        return service.guard(request);
    }
}
