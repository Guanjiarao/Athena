package athena.rank.biz.constant;

public final class RankConstants {

    public static final String RANK_KEY_PREFIX = "athena:rank:";

    public static final String RANK_IDEMPOTENT_KEY_PREFIX = "athena:rank:idempotent:";

    public static final String RANK_SEGMENT_TREE_KEY_PREFIX = "athena:rank:segment:";

    public static final long DEFAULT_BASE_TIME_SECONDS = 1_762_934_400L;

    public static final long DAY_SECONDS = 86_400L;

    private RankConstants() {
    }
}
