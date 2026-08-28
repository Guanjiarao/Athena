package com.whu.software.athena.cognitionagent.intent.web;

import com.whu.software.athena.cognitionagent.intent.context.IntentContextBuilder;
import com.whu.software.athena.cognitionagent.intent.context.IntentModelContextBuilder;
import com.whu.software.athena.cognitionagent.intent.contract.AgentError;
import com.whu.software.athena.cognitionagent.intent.contract.AgentErrorCode;
import com.whu.software.athena.cognitionagent.intent.contract.IntentClassificationRequest;
import com.whu.software.athena.cognitionagent.intent.provider.IntentModelProbeResponse;
import com.whu.software.athena.cognitionagent.intent.provider.IntentModelProvider;
import com.whu.software.athena.cognitionagent.intent.provider.IntentModelProviderException;
import com.whu.software.athena.cognitionagent.intent.validation.IntentRequestValidator;
import com.whu.software.athena.cognitionagent.intent.validation.ValidationIssue;
import com.whu.software.athena.cognitionagent.intent.validation.ValidationResult;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Local-only endpoint for verifying a real model credential and response shape.
 * It is unavailable unless the explicit model-probe profile is enabled.
 */
@Profile("model-probe")
@RestController
@RequestMapping("/internal/v1/cognition/nodes")
public class IntentModelProbeController {

    private final IntentRequestValidator requestValidator = new IntentRequestValidator();
    private final IntentContextBuilder contextBuilder = new IntentContextBuilder();
    private final IntentModelContextBuilder modelContextBuilder = new IntentModelContextBuilder();
    private final IntentModelProvider modelProvider;

    public IntentModelProbeController(IntentModelProvider modelProvider) {
        this.modelProvider = modelProvider;
    }

    @PostMapping(
            path = "/intent-classification/model-probe",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<IntentModelProbeResponse> probe(
            @RequestBody(required = false) IntentClassificationRequest request) {
        ValidationResult validation = requestValidator.validate(request);
        if (!validation.isValid()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(IntentModelProbeResponse.failure(errorFrom(validation.firstIssue())));
        }

        try {
            return ResponseEntity.ok(IntentModelProbeResponse.success(
                    modelProvider.suggest(modelContextBuilder.build(
                            contextBuilder.build(request.clue)))));
        } catch (IntentModelProviderException exception) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(IntentModelProbeResponse.failure(new AgentError(
                            exception.errorCode(), exception.getMessage(), exception.retryable(), null, null)));
        } catch (RuntimeException exception) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(IntentModelProbeResponse.failure(new AgentError(
                            AgentErrorCode.INTERNAL_ERROR, "model probe failed", false, null, null)));
        }
    }

    private AgentError errorFrom(ValidationIssue issue) {
        return new AgentError(issue.code(), issue.message(), false, issue.field(), null);
    }
}
