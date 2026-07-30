package athena.ground.biz.mq.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NoteTopicBuildEvent implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String eventId;

    private Long noteId;

    private Long authorId;

    private String title;

    private String content;

    private Integer type;

    private Integer channelId;
}
