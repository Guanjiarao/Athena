package com.whu.software.athena.cognition;

/** Stable mapping for Contract V1 errors. UI never branches on the Chinese server message. */
public final class CognitionErrorMessages {
    private CognitionErrorMessages() {}

    public static String toUserMessage(int httpCode, int code, String errorCode) {
        return toUserMessage(httpCode, code, errorCode, null);
    }

    public static String toUserMessage(int httpCode, int code, String errorCode, String serverMessage) {
        if ("COGNITION_INVALID_ARGUMENT".equals(errorCode)) return "提交内容不完整，请检查后重试";
        if ("COGNITION_NOT_FOUND".equals(errorCode)) return "这条内容不存在或已被删除";
        if ("COGNITION_STATE_CONFLICT".equals(errorCode)) return "状态已经更新，请刷新后重试";
        if ("COGNITION_VERSION_CONFLICT".equals(errorCode)) return "内容已在其他位置更新，正在刷新最新版本";
        if ("COGNITION_CLUE_IN_USE".equals(errorCode)) return "这条线索已经进入整理草稿，不能撤销";
        if ("COGNITION_NO_VALID_EVIDENCE".equals(errorCode)) return "还没有可整理的相关线索，请先标记“和我有关”";
        if ("COGNITION_TASK_RUNNING".equals(errorCode)) return "这个主题已有一份待确认草稿";
        if ("COGNITION_GENERATION_FAILED".equals(errorCode)) return "整理失败，请稍后重试";
        if (isAuthenticationFailure(httpCode, code, serverMessage)) return "登录已失效，请重新登录";
        return "请求失败，请稍后重试";
    }

    public static boolean isAuthenticationFailure(int httpCode, int code, String serverMessage) {
        if (httpCode == 401 || httpCode == 403 || code == 401 || code == 403) return true;
        if (serverMessage == null) return false;
        String message = serverMessage.trim().toLowerCase();
        return message.contains("token") && (message.contains("无效")
                || message.contains("失效") || message.contains("读取")
                || message.contains("invalid") || message.contains("expired"));
    }
}
