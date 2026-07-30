

package com.nageoffer.ai.ragent.triage.slot;

public class CorrectionPhraseParser {

    public ParsedCorrectionPhrase parse(String text) {
        if (!hasCorrectionCue(text)) {
            return null;
        }
        String confirm = confirm(text);
        if (blank(confirm)) {
            return null;
        }
        return new ParsedCorrectionPhrase(trim(reject(text)), trim(confirm), text);
    }

    public boolean hasCorrectionCue(String text) {
        return text != null
                && (text.matches(".*不是.+[，,。 ]?是.+")
                || text.contains("改成")
                || text.contains("更正")
                || text.contains("不对"));
    }

    private String reject(String text) {
        int notIndex = text.indexOf("不是");
        int isIndex = text.indexOf("，是") >= 0 ? text.indexOf("，是") : text.indexOf("是");
        if (notIndex < 0 || isIndex <= notIndex + 2) {
            return null;
        }
        return text.substring(notIndex + 2, isIndex).replace("，", "").trim();
    }

    private String confirm(String text) {
        if (text.contains("不对") && text.contains("是")) {
            int isIndex = text.indexOf("是");
            if (isIndex >= 0 && isIndex + 1 < text.length()) {
                return text.substring(isIndex + 1).trim();
            }
        }
        int isIndex = text.indexOf("，是");
        if (isIndex >= 0 && isIndex + 2 < text.length()) {
            return text.substring(isIndex + 2).trim();
        }
        isIndex = text.indexOf("是");
        if (isIndex >= 0 && isIndex + 1 < text.length()) {
            return text.substring(isIndex + 1).trim();
        }
        if (text.contains("改成")) {
            return text.substring(text.indexOf("改成") + 2).trim();
        }
        return null;
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private String trim(String value) {
        return blank(value) ? null : value.trim();
    }

    public record ParsedCorrectionPhrase(String rejectValue, String confirmValue, String evidence) {
    }
}
