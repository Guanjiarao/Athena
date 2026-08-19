package athena.cognition.biz.generator;

import athena.cognition.biz.domain.CognitionModels.ClueView;
import athena.cognition.biz.domain.CognitionModels.MarkIntent;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class FixedDigestGenerator implements DigestGenerator {

    @Override
    public GeneratedDigest generate(List<ClueView> clues) {
        if (clues == null || clues.isEmpty()) {
            throw new IllegalArgumentException("clues must not be empty");
        }

        boolean onlyQuestions = clues.stream().allMatch(clue -> clue.markIntent() == MarkIntent.QUESTION);
        String title = onlyQuestions ? "一个正在了解的问题" : "一项值得继续观察的身体线索";
        String commonPoint = onlyQuestions
                ? "你保存的这些内容都表示有问题想继续弄明白。"
                : "你保存的这些内容都被标记为可能和自己有关。";

        return new GeneratedDigest(
                title,
                commonPoint,
                "这些输入可以作为后续观察的起点，但目前只存在内容或时间上的初步联系。",
                "仅凭文章标记或疑问，不能确认你出现了相同情况，也不能说明原因或形成诊断。",
                "接下来 7 天可以完成一次相关身体记录，再回来确认这项观察是否仍然适合你。",
                "fixed-v1.0"
        );
    }
}
