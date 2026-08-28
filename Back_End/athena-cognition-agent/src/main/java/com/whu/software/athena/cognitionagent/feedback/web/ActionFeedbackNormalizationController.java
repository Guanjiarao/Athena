package com.whu.software.athena.cognitionagent.feedback.web;

import com.whu.software.athena.cognitionagent.feedback.contract.ActionFeedbackNormalizationRequest;
import com.whu.software.athena.cognitionagent.feedback.contract.ActionFeedbackNormalizationResponse;
import com.whu.software.athena.cognitionagent.feedback.service.ActionFeedbackNormalizationService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/cognition/nodes/action-feedback-normalization")
public class ActionFeedbackNormalizationController {

    private final ActionFeedbackNormalizationService service;

    public ActionFeedbackNormalizationController(ActionFeedbackNormalizationService service) {
        this.service = service;
    }

    @PostMapping
    public ActionFeedbackNormalizationResponse normalize(
            @RequestBody ActionFeedbackNormalizationRequest request) {
        return service.normalize(request);
    }
}
