package com.whu.software.athena.cognitionagent.patch.web;

import com.whu.software.athena.cognitionagent.patch.contract.GraphPatchAssemblyRequest;
import com.whu.software.athena.cognitionagent.patch.contract.GraphPatchAssemblyResponse;
import com.whu.software.athena.cognitionagent.patch.service.GraphPatchAssemblyService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/cognition/nodes/graph-patch-assembly")
public class GraphPatchAssemblyController {

    private final GraphPatchAssemblyService service;

    public GraphPatchAssemblyController(GraphPatchAssemblyService service) {
        this.service = service;
    }

    @PostMapping
    public GraphPatchAssemblyResponse assemble(
            @RequestBody GraphPatchAssemblyRequest request) {
        return service.assemble(request);
    }
}
