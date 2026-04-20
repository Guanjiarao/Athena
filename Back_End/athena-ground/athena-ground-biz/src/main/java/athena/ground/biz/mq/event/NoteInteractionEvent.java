package athena.ground.biz.mq.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 笔记互动事件
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NoteInteractionEvent implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String eventId;

    private Long userId;

    private Long noteId;

    /**
     * LIKE / UNLIKE / COLLECT / UNCOLLECT
     */
    private String actionType;

    /**
     * 计数增量：1 / -1
     */
    private Long delta;
}
