

package com.nageoffer.ai.ragent.triage.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.nageoffer.ai.ragent.triage.engine.TriageEvent;
import com.nageoffer.ai.ragent.triage.engine.TriageState;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Immutable audit entry recorded for every FSM transition.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuditLog {

    private Instant timestamp;

    private TriageState previousState;

    private TriageEvent triggerEvent;

    private TriageState currentState;

    private String decisionBasis;
}
