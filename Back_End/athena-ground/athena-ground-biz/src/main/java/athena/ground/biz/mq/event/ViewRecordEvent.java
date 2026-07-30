package athena.ground.biz.mq.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 浏览记录事件
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ViewRecordEvent implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long userId;

    private Long noteId;

    private String viewTime;

    private Integer duration;
}
