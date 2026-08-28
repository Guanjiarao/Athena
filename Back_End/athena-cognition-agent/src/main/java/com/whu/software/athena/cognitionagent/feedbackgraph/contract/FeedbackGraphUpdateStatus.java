package com.whu.software.athena.cognitionagent.feedbackgraph.contract;

public enum FeedbackGraphUpdateStatus {
    READY_FOR_CONFIRMATION,
    NO_CHANGE,
    STALE,
    BLOCKED,
    REJECTED,
    FAILED
}
