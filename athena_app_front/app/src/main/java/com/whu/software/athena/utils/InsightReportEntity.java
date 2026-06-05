package com.whu.software.athena.utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

public class InsightReportEntity {

    public int code;
    @Nullable
    public String message;
    @Nullable
    public ReportData data;

    public static class ReportData {
        @Nullable
        public String summary;
        @Nullable
        public String summarySource;
        @NonNull
        public List<String> healthFocuses = new ArrayList<>();
        @NonNull
        public List<String> contentFocuses = new ArrayList<>();
        @NonNull
        public List<String> riskTags = new ArrayList<>();
        @NonNull
        public List<String> recommendTopics = new ArrayList<>();
        @NonNull
        public List<ReadingSuggestion> readingSuggestions = new ArrayList<>();
    }

    public static class ReadingSuggestion {
        public long noteId;
        public int type;
        @Nullable
        public String title;
        @NonNull
        public List<String> topics = new ArrayList<>();
        @Nullable
        public String reason;
        public double score;
    }
}
