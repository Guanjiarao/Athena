package com.whu.software.athena.cognitionagent.intent.web;

import com.whu.software.athena.cognitionagent.intent.contract.IntentClassificationRequest;
import com.whu.software.athena.cognitionagent.intent.contract.IntentClassificationResponse;
import com.whu.software.athena.cognitionagent.intent.service.IntentClassificationService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Local synchronous entry point for the first node. */
@RestController
@RequestMapping("/internal/v1/cognition/nodes")
public class IntentClassificationController {

    private final IntentClassificationService classificationService;

    public IntentClassificationController(IntentClassificationService classificationService) {
        this.classificationService = classificationService;
    }

    @PostMapping(
            path = "/intent-classification",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public IntentClassificationResponse classify(
            @RequestBody(required = false) IntentClassificationRequest request) {
        // A business rejection is returned using the same response contract.
        return classificationService.classify(request);
    }
}
