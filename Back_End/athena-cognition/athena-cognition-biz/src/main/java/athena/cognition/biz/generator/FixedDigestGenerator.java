package athena.cognition.biz.generator;

import athena.cognition.biz.domain.CognitionModels.ClueIntent;
import athena.cognition.biz.domain.CognitionModels.ClueView;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * First-handover fixed generator (contract section 9). Fills structured digest
 * fields with deterministic copy that keeps explicit uncertainty and never
 * rewrites questions or saved knowledge into body facts.
 */
@Component
public class FixedDigestGenerator implements DigestGenerator {

    @Override
    public GeneratedDigest generate(List<ClueView> clues, String suggestedTitle) {
        if (clues == null || clues.isEmpty()) {
            throw new IllegalArgumentException("clues must not be empty");
        }

        boolean onlyQuestions = clues.stream().allMatch(clue -> clue.intent() == ClueIntent.QUESTION);
        String title = suggestedTitle != null && !suggestedTitle.isBlank()
                ? suggestedTitle.trim()
                : (onlyQuestions ? "一个正在了解的问题" : "一项值得继续观察的身体线索");
        String commonPoint = onlyQuestions
                ? "你保存的这些内容都表示有问题想继续弄明白。"
                : "你保存的这些内容都被标记为可能和自己有关。";

        return new GeneratedDigest(
                title,
                commonPoint,
                "这些线索在内容和时间上出现联系，但目前只能视为待确认的初步联系。",
                "还不能确定这种联系是否会重复，也不能仅凭这些信息形成身体结论或诊断。",
                "下次出现相关变化时，再记录一次时间和程度。",
                FIXED_VERSION
        );
    }
}
