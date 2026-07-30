package athena.ground.biz.constant;

/**
 * 笔记互动异步计数相关常量
 */
public class NoteInteractionConstants {

    /** RocketMQ Topic */
    public static final String NOTE_INTERACTION_TOPIC = "note-interaction-topic";

    /** RocketMQ 消费者组 */
    public static final String NOTE_INTERACTION_CONSUMER_GROUP = "note-interaction-consumer-group";

    /** RocketMQ 业务描述 */
    public static final String NOTE_INTERACTION_BIZ_DESC = "笔记互动异步计数";

    /** Redis 幂等 key 前缀 */
    public static final String NOTE_INTERACTION_IDEMPOTENT_KEY_PREFIX = "note:interaction:event:";

    public static final String ACTION_LIKE = "LIKE";
    public static final String ACTION_UNLIKE = "UNLIKE";
    public static final String ACTION_COLLECT = "COLLECT";
    public static final String ACTION_UNCOLLECT = "UNCOLLECT";

    private NoteInteractionConstants() {
    }
}
