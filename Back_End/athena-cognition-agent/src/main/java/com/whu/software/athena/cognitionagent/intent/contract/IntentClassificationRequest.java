package com.whu.software.athena.cognitionagent.intent.contract;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class IntentClassificationRequest {

    @NotBlank
    public String contractVersion;

    @NotBlank
    public String nodeVersion;

    @NotBlank
    public String runId;

    @NotBlank
    public String idempotencyKey;

    @NotNull
    public TriggerType triggerType;

    @NotBlank
    public String contextSnapshotId;

    @NotNull
    @Valid
    public CluePayload clue;
}
