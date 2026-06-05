package com.whu.software.athena.features.chat.history;

import java.io.Serializable;

public class ConversationSummary implements Serializable {

    private final String conversationId;
    private final String title;
    private final long sortTimeMillis;
    private final String displayTime;

    public ConversationSummary(String conversationId,
                               String title,
                               long sortTimeMillis,
                               String displayTime) {
        this.conversationId = conversationId;
        this.title = title;
        this.sortTimeMillis = sortTimeMillis;
        this.displayTime = displayTime;
    }

    public String getConversationId() {
        return conversationId;
    }

    public String getTitle() {
        return title;
    }

    public long getSortTimeMillis() {
        return sortTimeMillis;
    }

    public String getDisplayTime() {
        return displayTime;
    }
}
