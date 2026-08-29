package com.whu.software.athena.cognitionagent.common.text;

/**
 * Guards user-visible model output text: it must be natural Simplified Chinese.
 * Pure-English answers (a known failure mode when the prompt/context is English)
 * are rejected; Chinese text that quotes a few English terms stays acceptable.
 */
public final class UserVisibleTextPolicy {

    /**
     * Tolerated volume of incidental Latin letters (units, acronyms, quoted terms).
     * Anything at or below this count is never rejected on its own.
     */
    static final int MAX_LATIN_LETTERS = 20;

    /**
     * A text is rejected when its Latin letters exceed {@link #MAX_LATIN_LETTERS}
     * AND outnumber half of its CJK characters (i.e. the text is mostly Latin).
     * A CJK count of 0 makes any overflow fail, so long pure-English text always fails.
     */
    static final double MAX_LATIN_PER_CJK = 0.5;

    /**
     * Correction hint appended to the user prompt when a model answer fails the
     * language check; providers retry the call exactly once with this hint.
     */
    public static final String CORRECTION_HINT =
            "\nYour previous answer used non-Chinese text in user-visible fields."
                    + " Rewrite all user-visible fields in natural Simplified Chinese"
                    + " and return the same JSON structure.";

    private UserVisibleTextPolicy() {
    }

    /**
     * @return true when the text is acceptable as user-visible Simplified Chinese.
     * Null and blank texts pass: presence/blankness is enforced by schema validation.
     */
    public static boolean isUserVisibleChinese(String text) {
        if (text == null || text.isBlank()) {
            return true;
        }
        int cjk = 0;
        int latin = 0;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (Character.UnicodeScript.of(ch) == Character.UnicodeScript.HAN) {
                cjk++;
            } else if ((ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z')) {
                latin++;
            }
        }
        if (latin <= MAX_LATIN_LETTERS) {
            return true;
        }
        return latin <= cjk * MAX_LATIN_PER_CJK;
    }
}
