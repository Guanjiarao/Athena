package athena.cognition.biz.generator;

import athena.cognition.biz.domain.CognitionModels.ClueView;

import java.util.List;

public interface DigestGenerator {

    GeneratedDigest generate(List<ClueView> clues);

    record GeneratedDigest(
            String title,
            String commonPoint,
            String possibleLink,
            String uncertainty,
            String suggestedAction,
            String generatorVersion
    ) {
    }
}
