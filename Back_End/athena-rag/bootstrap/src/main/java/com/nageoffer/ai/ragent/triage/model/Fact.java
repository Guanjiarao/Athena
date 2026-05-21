

package com.nageoffer.ai.ragent.triage.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Atomic structured fact extracted from one user turn.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Fact {

    private FactType type;

    private SlotCode slot;

    private String canonicalValue;

    private FactPolarity polarity;

    private Double confidence;

    private String evidence;

    private Integer sourceTurnIndex;

    private String sourceText;
}
