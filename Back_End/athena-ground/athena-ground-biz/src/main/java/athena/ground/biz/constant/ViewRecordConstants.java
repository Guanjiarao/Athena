package athena.ground.biz.constant;

/**
 * 浏览记录相关常量
 */
public class ViewRecordConstants {

    /** Redis key 前缀：用户最近浏览 ZSet */
    public static final String VIEW_RECENT_KEY_PREFIX = "view:recent:";

    /** 最近浏览最大保留条数 */
    public static final long VIEW_RECENT_MAX_SIZE = 200;

    /** RocketMQ Topic */
    public static final String VIEW_RECORD_TOPIC = "view-record-topic";

    /** RocketMQ 消费者组 */
    public static final String VIEW_RECORD_CONSUMER_GROUP = "view-record-consumer-group";

    /** RocketMQ 业务描述 */
    public static final String VIEW_RECORD_BIZ_DESC = "浏览记录异步落库";
}
