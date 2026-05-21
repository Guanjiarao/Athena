

package com.nageoffer.ai.ragent.triage.response;

import cn.hutool.core.util.StrUtil;
import com.nageoffer.ai.ragent.triage.controller.vo.TriageAnalyzeResponse;
import com.nageoffer.ai.ragent.triage.model.TriageAction;
import com.nageoffer.ai.ragent.triage.model.TriageContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TriageResponseAgent {

    private final ClarificationResponseAssembler clarificationResponseAssembler;
    private final WarningResponseAssembler warningResponseAssembler;
    private final ReportResponseAssembler reportResponseAssembler;

    public TriageAnalyzeResponse toResponse(TriageContext context) {
        TriageAction action = context.getNextAction();
        if (action == null) {
            context.setNextAction(TriageAction.ASK_CLARIFICATION);
            if (StrUtil.isBlank(context.getFinalReply())) {
                context.setFinalReply("为了继续判断，请再补充一些不适细节。");
            }
            return clarificationResponseAssembler.assemble(context);
        }
        return switch (action) {
            case ASK_CLARIFICATION -> clarificationResponseAssembler.assemble(context);
            case TRIGGER_WARNING -> warningResponseAssembler.assemble(context);
            case GENERATE_REPORT -> reportResponseAssembler.assemble(context);
        };
    }
}
