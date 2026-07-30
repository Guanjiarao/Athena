

package com.nageoffer.ai.ragent.knowledge.mq.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * Athena 笔记同步事件
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AthenaNoteSyncEvent implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long noteId;

    private String title;

    private String contentHtml;

    private Integer type;

    private Long authorId;
}
