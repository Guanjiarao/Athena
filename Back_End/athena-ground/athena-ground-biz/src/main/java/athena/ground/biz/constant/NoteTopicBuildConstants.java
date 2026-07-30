package athena.ground.biz.constant;

/**
 * 笔记 Topic 构建相关常量
 */
public class NoteTopicBuildConstants {

    /** RocketMQ Topic */
    public static final String NOTE_TOPIC_BUILD_TOPIC = "note-topic-build-topic";

    /** RocketMQ 消费者组 */
    public static final String NOTE_TOPIC_BUILD_CONSUMER_GROUP = "note-topic-build-consumer-group";

    /** RocketMQ 业务描述 */
    public static final String NOTE_TOPIC_BUILD_BIZ_DESC = "笔记Topic异步构建";

    /** Redis 幂等 key 前缀 */
    public static final String NOTE_TOPIC_BUILD_IDEMPOTENT_KEY_PREFIX = "note:topic:build:event:";

    /** source_type: 规则构建 */
    public static final byte SOURCE_TYPE_RULE = 2;

    private NoteTopicBuildConstants() {
    }
}
