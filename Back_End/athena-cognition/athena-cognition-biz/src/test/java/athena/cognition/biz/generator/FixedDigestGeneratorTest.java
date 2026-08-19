package athena.cognition.biz.generator;

import athena.cognition.biz.domain.CognitionModels.*;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FixedDigestGeneratorTest {

    private final FixedDigestGenerator generator = new FixedDigestGenerator();

    @Test
    void questionClueIsNotRewrittenAsBodyFact() {
        ClueView question = clue(MarkIntent.QUESTION);

        DigestGenerator.GeneratedDigest result = generator.generate(List.of(question));

        assertThat(result.title()).contains("问题");
        assertThat(result.uncertainty()).contains("不能确认").contains("不能说明原因");
        assertThat(result.commonPoint()).doesNotContain("你出现了");
    }

    @Test
    void relatedArticleMarkKeepsExplicitUncertainty() {
        DigestGenerator.GeneratedDigest result = generator.generate(List.of(clue(MarkIntent.RELATED)));

        assertThat(result.possibleLink()).contains("初步联系");
        assertThat(result.uncertainty()).contains("不能").contains("诊断");
    }

    private ClueView clue(MarkIntent intent) {
        return new ClueView(1, ClueType.ARTICLE_MARK, intent, RelationDetail.UNCERTAIN_OBSERVE,
                "OBSERVE", "a-1", "文章", "reviewed", "摘录", "COMMON", "这常见吗",
                null, Instant.now(), ClueStatus.PENDING, Instant.now());
    }
}
