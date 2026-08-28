package com.whu.software.athena.cognitionagent.feedbackworkflow.web;

import com.whu.software.athena.cognitionagent.feedbackworkflow.contract.ActionFeedbackWorkflowRequest;
import com.whu.software.athena.cognitionagent.feedbackworkflow.contract.ActionFeedbackWorkflowResponse;
import com.whu.software.athena.cognitionagent.feedbackworkflow.service.ActionFeedbackWorkflow;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/cognition/workflows/action-feedback/prepare")
public class ActionFeedbackWorkflowController {

    private final ActionFeedbackWorkflow workflow;

    public ActionFeedbackWorkflowController(ActionFeedbackWorkflow workflow) {
        this.workflow = workflow;
    }

    @PostMapping
    public ActionFeedbackWorkflowResponse prepare(
            @RequestBody ActionFeedbackWorkflowRequest request) {
        return workflow.prepare(request);
    }
}
