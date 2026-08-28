package com.whu.software.athena.cognitionagent.scope.web;

import com.whu.software.athena.cognitionagent.scope.contract.GraphUpdateScopeRequest;
import com.whu.software.athena.cognitionagent.scope.contract.GraphUpdateScopeResponse;
import com.whu.software.athena.cognitionagent.scope.service.GraphUpdateScopePlanningService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/cognition/nodes/graph-update-scope")
public class GraphUpdateScopeController {

    private final GraphUpdateScopePlanningService service;

    public GraphUpdateScopeController(GraphUpdateScopePlanningService service) {
        this.service = service;
    }

    @PostMapping
    public GraphUpdateScopeResponse plan(@RequestBody GraphUpdateScopeRequest request) {
        return service.plan(request);
    }
}
