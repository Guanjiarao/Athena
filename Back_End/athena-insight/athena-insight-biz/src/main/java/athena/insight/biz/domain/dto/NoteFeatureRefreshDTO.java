package athena.insight.biz.domain.dto;

import lombok.Data;

@Data
public class NoteFeatureRefreshDTO {

    private Long noteId;

    private Integer pageNum;

    private Integer pageSize;
}
