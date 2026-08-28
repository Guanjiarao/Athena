package com.whu.software.athena.cognitionagent.action.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.whu.software.athena.cognitionagent.action.context.NextActionModelContext;

public class NextActionPromptBuilder {

    private final ObjectMapper mapper;

    public NextActionPromptBuilder(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public String systemPrompt() {
        return "You plan exactly one low-burden observation action for a personal cognition "
                + "topic. Use only an allowedActionType. Do not diagnose, estimate risk, "
                + "recommend treatment or medication, create database instructions, or claim "
                + "that article content is a body fact. Return a JSON object with exactly these "
                + "keys: actionType (one of the allowedActionType values), title (string), "
                + "description (string), evidenceIds (string array), rationale (string). Do not "
                + "add any other keys. Do not wrap the JSON in markdown fences.";
    }

    public String userPrompt(NextActionModelContext context) {
        try {
            return "Plan one action using only this context. Every evidenceIds value must come "
                    + "from evidences. Keep the title under 80 characters and description under "
                    + "500 characters.\n" + mapper.writeValueAsString(context);
        } catch (Exception exception) {
            throw new IllegalStateException("cannot serialize next action context", exception);
        }
    }
}
