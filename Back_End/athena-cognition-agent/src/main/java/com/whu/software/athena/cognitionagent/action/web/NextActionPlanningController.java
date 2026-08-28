package com.whu.software.athena.cognitionagent.action.web;

import com.whu.software.athena.cognitionagent.action.contract.NextActionPlanningRequest;
import com.whu.software.athena.cognitionagent.action.contract.NextActionPlanningResponse;
import com.whu.software.athena.cognitionagent.action.service.NextActionPlanningService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/cognition/nodes/next-action-planning")
public class NextActionPlanningController {

    private final NextActionPlanningService service;

    public NextActionPlanningController(NextActionPlanningService service) {
        this.service = service;
    }

    @PostMapping
    public NextActionPlanningResponse plan(@RequestBody NextActionPlanningRequest request) {
        return service.plan(request);
    }
}
