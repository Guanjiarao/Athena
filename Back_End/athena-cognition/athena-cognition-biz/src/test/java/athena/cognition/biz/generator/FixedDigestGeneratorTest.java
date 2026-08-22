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
        DigestGenerator.GeneratedDigest result = generator.generate(List.of(clue(ClueIntent.QUESTION)), null);

        assertThat(result.title()).contains("问题");
        assertThat(result.uncertainty()).contains("不能").contains("诊断");
        assertThat(result.commonPoint()).doesNotContain("你出现了");
        assertThat(result.generatorVersion()).isEqualTo("fixed-v1");
    }

    @Test
    void relatedArticleMarkKeepsExplicitUncertainty() {
        DigestGenerator.GeneratedDigest result = generator.generate(List.of(clue(ClueIntent.RELATED)), null);

        assertThat(result.possibleRelation()).contains("初步联系");
        assertThat(result.uncertainty()).contains("不能").contains("诊断");
    }

    @Test
    void suggestedTitleWins() {
        DigestGenerator.GeneratedDigest result = generator.generate(List.of(clue(ClueIntent.RELATED)), " 经前情绪变化 ");

        assertThat(result.title()).isEqualTo("经前情绪变化");
    }

    private ClueView clue(ClueIntent intent) {
        return new ClueView("clue_1", ClueType.ARTICLE_HIGHLIGHT, intent, RelationType.OBSERVE,
                HelpRequestType.OBSERVE, "1024", "文章", 100, "摘录",
                QuestionType.IS_COMMON, "这常见吗", null, CycleRelation.UNKNOWN, null, null,
                ClueSource.KNOWLEDGE_ARTICLE, ClueStatus.PENDING, null, null, "和我有关",
                Instant.now(), Instant.now());
    }
}
