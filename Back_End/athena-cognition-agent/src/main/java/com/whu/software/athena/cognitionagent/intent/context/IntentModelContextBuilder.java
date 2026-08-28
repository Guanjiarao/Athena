package com.whu.software.athena.cognitionagent.intent.context;

/** Narrows the node's internal context before any data is sent to a model. */
public class IntentModelContextBuilder {

    public IntentModelContext build(IntentContext context) {
        if (context == null) {
            throw new IllegalArgumentException("context must not be null");
        }
        return new IntentModelContext(
                context.intent(),
                context.relationType(),
                context.helpRequestType(),
                context.articleTitle(),
                context.selectedText(),
                context.questionType(),
                context.questionText(),
                context.cycleRelation()
        );
    }
}
