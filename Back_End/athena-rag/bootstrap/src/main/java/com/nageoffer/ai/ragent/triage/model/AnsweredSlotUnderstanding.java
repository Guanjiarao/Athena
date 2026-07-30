

package com.nageoffer.ai.ragent.triage.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AnsweredSlotUnderstanding {

    private SlotCode slot;

    private String rawValue;

    private String normalizedValue;

    private AssertionStatus assertion;

    private Double confidence;

    private String evidence;

    private Boolean answersPreviousQuestion;
}
