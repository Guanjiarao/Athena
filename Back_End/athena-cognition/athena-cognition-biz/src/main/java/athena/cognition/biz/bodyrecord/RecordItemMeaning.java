package athena.cognition.biz.bodyrecord;

/**
 * Contract section 4.8: stable meaning mapping of daily_record.recordItemId,
 * agreed with athena-record (current: 3=症状, 4=心情). The cognition service
 * and any future Agent must never guess the meaning of raw numbers; extend
 * this server-side constant when athena-record adds items.
 */
public enum RecordItemMeaning {

    SYMPTOM(3, "症状"),
    MOOD(4, "心情");

    private final int recordItemId;
    private final String label;

    RecordItemMeaning(int recordItemId, String label) {
        this.recordItemId = recordItemId;
        this.label = label;
    }

    public int recordItemId() {
        return recordItemId;
    }

    /** Items that count as user-confirmed body facts for RULE_2 and maturity. */
    public static boolean isBodyFactItem(Integer recordItemId) {
        return meaningOf(recordItemId) != null;
    }

    /** Display snapshot prefix, e.g. "症状：乳房胀痛". */
    public static String summaryOf(Integer recordItemId, String recordValue) {
        RecordItemMeaning meaning = meaningOf(recordItemId);
        String label = meaning == null ? "记录" : meaning.label;
        return recordValue == null || recordValue.isBlank() ? label : label + "：" + recordValue;
    }

    private static RecordItemMeaning meaningOf(Integer recordItemId) {
        if (recordItemId == null) return null;
        for (RecordItemMeaning meaning : values()) {
            if (meaning.recordItemId == recordItemId) return meaning;
        }
        return null;
    }
}
