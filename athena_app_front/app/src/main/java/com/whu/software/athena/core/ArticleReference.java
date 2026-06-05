package com.whu.software.athena.core;

public class ArticleReference {
    private final long noteId;
    private final String blogId;
    private final String title;
    private final String snippet;
    private final int articleType;

    public ArticleReference(long noteId, String title, String snippet) {
        this(noteId, noteId > 0 ? String.valueOf(noteId) : "", title, snippet, 0);
    }

    public ArticleReference(long noteId,
                            String blogId,
                            String title,
                            String snippet,
                            int articleType) {
        this.noteId = noteId;
        this.blogId = blogId == null ? "" : blogId;
        this.title = title;
        this.snippet = snippet;
        this.articleType = articleType;
    }

    public long getNoteId() {
        return noteId;
    }

    public String getBlogId() {
        if (blogId != null && !blogId.trim().isEmpty()) {
            return blogId;
        }
        return noteId > 0 ? String.valueOf(noteId) : "";
    }

    public String getTitle() {
        return title;
    }

    public String getSnippet() {
        return snippet;
    }

    public int getArticleType() {
        return articleType;
    }

    @Override
    public String toString() {
        return "ArticleReference{"
                + "noteId=" + noteId
                + ", blogId='" + blogId + '\''
                + ", title='" + title + '\''
                + ", snippet='" + snippet + '\''
                + ", articleType=" + articleType
                + '}';
    }
}
