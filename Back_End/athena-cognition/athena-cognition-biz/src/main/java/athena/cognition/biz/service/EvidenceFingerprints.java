package athena.cognition.biz.service;

import athena.cognition.biz.rpc.agent.dto.EvidenceSourceType;
import athena.cognition.biz.rpc.agent.dto.GraphActionFeedbackResult;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.Locale;

/**
 * Content fingerprint of canonical evidence. Mirrors the Agent-side
 * EvidenceCanonicalizationService algorithm exactly (NFKC normalize, lowercase,
 * whitespace collapse, SHA-256 hex over "sourceType|summary[|occurredAt][|relatedActionId|feedbackResult]"),
 * so fingerprints computed here match the ones the Agent computes for
 * candidates and deduplication (node 2) works across runs.
 */
public final class EvidenceFingerprints {

    private EvidenceFingerprints() {
    }

    public static String fingerprint(EvidenceSourceType sourceType, String summary, String occurredAt,
                                     String relatedActionId, GraphActionFeedbackResult feedbackResult) {
        StringBuilder material = new StringBuilder(sourceType.name()).append('|').append(normalized(summary));
        if (sourceType == EvidenceSourceType.BODY_RECORD || sourceType == EvidenceSourceType.ACTION_FEEDBACK) {
            material.append('|').append(normalized(occurredAt));
        }
        if (sourceType == EvidenceSourceType.ACTION_FEEDBACK) {
            material.append('|').append(normalized(relatedActionId)).append('|').append(feedbackResult);
        }
        return sha256Hex(material.toString());
    }

    public static String normalized(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
    }

    private static String sha256Hex(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(bytes.length * 2);
            for (byte item : bytes) result.append(String.format("%02x", item));
            return result.toString();
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
