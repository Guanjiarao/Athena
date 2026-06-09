package athena.count.biz.constant;

public final class CountConstants {

    public static final String SCOPE_NOTE = "note";
    public static final String SCOPE_USER = "user";

    public static final String NOTE_LIKE_TOTAL = "likeTotal";
    public static final String NOTE_COLLECT_TOTAL = "collectTotal";
    public static final String NOTE_COMMENT_TOTAL = "commentTotal";
    public static final String NOTE_SHARE_TOTAL = "shareTotal";
    public static final String NOTE_FORWARD_TOTAL = "forwardTotal";

    public static final String USER_FOLLOWER_TOTAL = "followerTotal";
    public static final String USER_FOLLOWING_TOTAL = "followingTotal";
    public static final String USER_LIKED_TOTAL = "likedTotal";

    public static final String COUNTER_KEY_PREFIX = "athena:count:";
    public static final String COUNTER_DIRTY_SET_KEY = "athena:count:dirty";
    public static final String EVENT_TOPIC = "athena_count_event_topic";
    public static final String EVENT_BIZ_DESC = "计数服务异步聚合计数";
    public static final String IDEMPOTENT_KEY_PREFIX = "athena:count:idempotent:";

    private CountConstants() {
    }
}
