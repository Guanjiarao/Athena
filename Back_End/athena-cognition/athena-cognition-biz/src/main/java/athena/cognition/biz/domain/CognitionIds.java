package athena.cognition.biz.domain;

/**
 * External string IDs (contract section 4, e.g. clue_1001) over internal
 * auto-increment numeric primary keys. The numeric id stays internal; the API
 * only ever sees the prefixed string form.
 */
public final class CognitionIds {

    public static final String CLUE = "clue";
    public static final String DIGEST = "digest";
    public static final String TASK = "task";
    public static final String TOPIC = "topic";
    public static final String ACTION = "action";
    public static final String FEEDBACK = "feedback";
    public static final String EVIDENCE = "evidence";

    private CognitionIds() {
    }

    public static String of(String prefix, long numericId) {
        return prefix + "_" + numericId;
    }

    /**
     * Parses an external id, verifying the expected prefix.
     *
     * @throws CognitionException with COGNITION_INVALID_ARGUMENT when malformed
     */
    public static long parse(String prefix, String externalId) {
        String expected = prefix + "_";
        if (externalId == null || !externalId.startsWith(expected)) {
            throw CognitionException.invalidArgument("ID 格式不正确");
        }
        try {
            long value = Long.parseLong(externalId.substring(expected.length()));
            if (value <= 0) {
                throw new NumberFormatException("non positive");
            }
            return value;
        } catch (NumberFormatException ex) {
            throw CognitionException.invalidArgument("ID 格式不正确");
        }
    }
}
