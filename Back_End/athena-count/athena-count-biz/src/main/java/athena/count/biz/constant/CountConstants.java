package athena.count.biz.constant;

import athena.count.api.constant.CountCounterConstants;

public final class CountConstants {

    public static final String SCOPE_NOTE = CountCounterConstants.SCOPE_NOTE;
    public static final String SCOPE_COMMENT = CountCounterConstants.SCOPE_COMMENT;
    public static final String SCOPE_USER = CountCounterConstants.SCOPE_USER;

    public static final String NOTE_LIKE_TOTAL = CountCounterConstants.LIKE_TOTAL;
    public static final String NOTE_COLLECT_TOTAL = CountCounterConstants.COLLECT_TOTAL;
    public static final String NOTE_COMMENT_TOTAL = CountCounterConstants.COMMENT_TOTAL;
    public static final String NOTE_SHARE_TOTAL = CountCounterConstants.SHARE_TOTAL;
    public static final String NOTE_FORWARD_TOTAL = CountCounterConstants.FORWARD_TOTAL;

    public static final String COMMENT_LIKE_TOTAL = CountCounterConstants.LIKE_TOTAL;

    public static final String USER_FOLLOWER_TOTAL = CountCounterConstants.FOLLOWER_TOTAL;
    public static final String USER_FOLLOWING_TOTAL = CountCounterConstants.FOLLOWING_TOTAL;
    public static final String USER_LIKED_TOTAL = CountCounterConstants.LIKED_TOTAL;

    public static final String COUNTER_KEY_PREFIX = "athena:count:";
    public static final String COUNTER_DIRTY_SET_KEY = "athena:count:dirty";
    public static final String EVENT_TOPIC = CountCounterConstants.EVENT_TOPIC;
    public static final String EVENT_BIZ_DESC = CountCounterConstants.EVENT_BIZ_DESC;
    public static final String IDEMPOTENT_KEY_PREFIX = "athena:count:idempotent:";

    private CountConstants() {
    }
}
