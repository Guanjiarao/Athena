package com.whu.software.athena.cognitionagent.workflow.web;

import com.whu.software.athena.cognitionagent.workflow.contract.GraphUpdatePreparationRequest;
import com.whu.software.athena.cognitionagent.workflow.contract.GraphUpdatePreparationResponse;
import com.whu.software.athena.cognitionagent.workflow.service.CognitionGraphWorkflow;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/cognition/workflows/graph-update/prepare")
public class CognitionGraphWorkflowController {

    private final CognitionGraphWorkflow workflow;

    public CognitionGraphWorkflowController(CognitionGraphWorkflow workflow) {
        this.workflow = workflow;
    }

    @PostMapping
    public GraphUpdatePreparationResponse prepare(
            @RequestBody GraphUpdatePreparationRequest request) {
        return workflow.prepare(request);
    }
}
