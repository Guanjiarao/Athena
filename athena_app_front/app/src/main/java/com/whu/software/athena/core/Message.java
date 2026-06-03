package com.whu.software.athena.core;

import java.util.ArrayList;
import java.util.List;

public class Message {
    private String role;
    private String content;
    private List<ArticleReference> references;

    /** 标记该消息是否正在播放打字动画，不参与 JSON 序列化。 */
    private transient boolean isTyping = false;

    public Message(String role, String content) {
        this.role = role;
        this.content = content;
        this.references = new ArrayList<>();
    }

    public String getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public void appendContent(String delta) {
        if (delta == null || delta.isEmpty()) {
            return;
        }
        if (content == null) {
            content = delta;
            return;
        }
        content += delta;
    }

    public List<ArticleReference> getReferences() {
        return references;
    }

    public void setReferences(List<ArticleReference> references) {
        this.references = references == null ? new ArrayList<>() : new ArrayList<>(references);
    }

    public boolean isTyping() {
        return isTyping;
    }

    public void setTyping(boolean typing) {
        isTyping = typing;
    }
}
