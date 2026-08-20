package athena.cognition.biz.generator;

import athena.cognition.biz.domain.CognitionModels.ClueView;

import java.util.List;

/**
 * Stable generator boundary (contract section 9). The first handover ships a
 * fixed implementation; the second handover swaps in the Agent implementation
 * without changing services, controllers or the database.
 */
public interface DigestGenerator {

    String FIXED_VERSION = "fixed-v1";

    GeneratedDigest generate(List<ClueView> clues, String suggestedTitle);

    record GeneratedDigest(
            String title,
            String commonPoint,
            String possibleRelation,
            String uncertainty,
            String suggestedAction,
            String generatorVersion
    ) {
    }
}
