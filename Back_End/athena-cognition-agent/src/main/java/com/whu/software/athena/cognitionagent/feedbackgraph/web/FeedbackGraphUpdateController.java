package com.whu.software.athena.cognitionagent.feedbackgraph.web;

import com.whu.software.athena.cognitionagent.feedbackgraph.contract.FeedbackGraphUpdateRequest;
import com.whu.software.athena.cognitionagent.feedbackgraph.contract.FeedbackGraphUpdateResponse;
import com.whu.software.athena.cognitionagent.feedbackgraph.service.FeedbackGraphUpdateService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/cognition/nodes/action-feedback-graph-update")
public class FeedbackGraphUpdateController {

    private final FeedbackGraphUpdateService service;

    public FeedbackGraphUpdateController(FeedbackGraphUpdateService service) {
        this.service = service;
    }

    @PostMapping
    public FeedbackGraphUpdateResponse prepare(
            @RequestBody FeedbackGraphUpdateRequest request) {
        return service.prepare(request);
    }
}
