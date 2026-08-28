package com.whu.software.athena.cognitionagent.intent.policy;

import com.whu.software.athena.cognitionagent.intent.contract.AgentErrorCode;
import com.whu.software.athena.cognitionagent.intent.contract.ClueIntent;
import com.whu.software.athena.cognitionagent.intent.contract.EvidenceClass;
import com.whu.software.athena.cognitionagent.intent.contract.FactEligibility;
import com.whu.software.athena.cognitionagent.intent.contract.IntentClassificationRequest;
import com.whu.software.athena.cognitionagent.intent.contract.IntentClassificationResponse;
import com.whu.software.athena.cognitionagent.intent.contract.IntentClassificationStatus;
import com.whu.software.athena.cognitionagent.intent.contract.NextRoute;
import com.whu.software.athena.cognitionagent.intent.contract.PolicyResult;

import java.util.List;

/** Enforces the first node's evidence and routing safety boundary. */
public class IntentPolicyValidator {

    public PolicyValidationResult validate(IntentClassificationRequest request,
                                           IntentClassificationResponse response) {
        if (request == null || request.clue == null || response == null) {
            return PolicyValidationResult.block(
                    AgentErrorCode.POLICY_BLOCKED,
                    null,
                    "request, clue, and response are required for policy validation");
        }

        if (response.status != IntentClassificationStatus.SUCCEEDED) {
            return PolicyValidationResult.block(
                    AgentErrorCode.POLICY_BLOCKED,
                    "status",
                    "only a succeeded classification can pass policy validation");
        }
        if (!request.clue.id.equals(response.clueId)) {
            return PolicyValidationResult.block(
                    AgentErrorCode.POLICY_BLOCKED,
                    "clueId",
                    "response clueId must match request clue.id");
        }
        if (response.evidenceIds == null || !response.evidenceIds.contains(request.clue.id)) {
            return PolicyValidationResult.block(
                    AgentErrorCode.POLICY_BLOCKED,
                    "evidenceIds",
                    "the current clue must be included as evidence");
        }

        if (response.intent == null || response.evidenceClass == null
                || response.factEligibility == null || response.nextRoute == null) {
            return PolicyValidationResult.block(
                    AgentErrorCode.POLICY_BLOCKED,
                    null,
                    "classification result is incomplete");
        }

        PolicyValidationResult mappingResult = validateMapping(response);
        if (!mappingResult.allowed()) {
            return mappingResult;
        }

        if (response.policyResult == PolicyResult.BLOCK) {
            return PolicyValidationResult.block(
                    AgentErrorCode.POLICY_BLOCKED,
                    "policyResult",
                    "response is already marked as blocked");
        }
        return PolicyValidationResult.pass();
    }

    private PolicyValidationResult validateMapping(IntentClassificationResponse response) {
        if (response.intent == ClueIntent.QUESTION) {
            if (response.evidenceClass != EvidenceClass.USER_QUESTION
                    || response.factEligibility != FactEligibility.NOT_BODY_FACT
                    || response.nextRoute != NextRoute.QUESTION_INBOX) {
                return PolicyValidationResult.block(
                        AgentErrorCode.POLICY_BLOCKED,
                        "intent",
                        "QUESTION must remain an unconfirmed question");
            }
        }

        if (response.intent == ClueIntent.KNOWLEDGE_ONLY) {
            if (response.evidenceClass != EvidenceClass.ARTICLE_KNOWLEDGE
                    || response.factEligibility != FactEligibility.NOT_BODY_FACT
                    || response.nextRoute != NextRoute.KNOWLEDGE_INBOX) {
                return PolicyValidationResult.block(
                        AgentErrorCode.POLICY_BLOCKED,
                        "intent",
                        "KNOWLEDGE_ONLY cannot enter body facts or topic evidence");
            }
        }

        if (response.intent == ClueIntent.RELATED) {
            if (response.factEligibility != FactEligibility.CANDIDATE_ONLY
                    || response.nextRoute != NextRoute.MATCH_EXISTING_TOPIC_CANDIDATE
                    || !List.of(EvidenceClass.USER_PERSONAL_CLAIM,
                    EvidenceClass.USER_DECLARED_RELEVANCE).contains(response.evidenceClass)) {
                return PolicyValidationResult.block(
                        AgentErrorCode.POLICY_BLOCKED,
                        "intent",
                        "RELATED may only enter candidate topic matching");
            }
        }
        return PolicyValidationResult.pass();
    }
}
