package athena.athenaframework.mq;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.UUID;

/**
 * 通用 MQ 消息包装器
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageWrapper<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 业务 key
     */
    private String keys;

    /**
     * 业务载荷
     */
    private T body;

    /**
     * 唯一消息标识
     */
    @Builder.Default
    private String uuid = UUID.randomUUID().toString();

    /**
     * 发送时间戳
     */
    @Builder.Default
    private Long timestamp = System.currentTimeMillis();
}
