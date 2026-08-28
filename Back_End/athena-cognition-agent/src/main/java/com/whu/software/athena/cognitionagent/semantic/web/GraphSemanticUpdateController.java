package com.whu.software.athena.cognitionagent.semantic.web;

import com.whu.software.athena.cognitionagent.semantic.contract.GraphSemanticUpdateRequest;
import com.whu.software.athena.cognitionagent.semantic.contract.GraphSemanticUpdateResponse;
import com.whu.software.athena.cognitionagent.semantic.service.GraphSemanticUpdateService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/cognition/nodes/graph-semantic-update")
public class GraphSemanticUpdateController {

    private final GraphSemanticUpdateService service;

    public GraphSemanticUpdateController(GraphSemanticUpdateService service) {
        this.service = service;
    }

    @PostMapping
    public GraphSemanticUpdateResponse generate(
            @RequestBody GraphSemanticUpdateRequest request) {
        return service.generate(request);
    }
}
