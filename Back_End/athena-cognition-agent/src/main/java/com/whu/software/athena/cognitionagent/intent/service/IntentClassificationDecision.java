package com.whu.software.athena.cognitionagent.intent.service;

import com.whu.software.athena.cognitionagent.intent.contract.AmbiguityCode;
import com.whu.software.athena.cognitionagent.intent.contract.ClueIntent;
import com.whu.software.athena.cognitionagent.intent.contract.DecisionSource;
import com.whu.software.athena.cognitionagent.intent.contract.EvidenceClass;
import com.whu.software.athena.cognitionagent.intent.contract.FactEligibility;
import com.whu.software.athena.cognitionagent.intent.contract.NextRoute;

public record IntentClassificationDecision(
        ClueIntent intent,
        EvidenceClass evidenceClass,
        FactEligibility factEligibility,
        DecisionSource decisionSource,
        AmbiguityCode ambiguityCode,
        NextRoute nextRoute
) {
}
