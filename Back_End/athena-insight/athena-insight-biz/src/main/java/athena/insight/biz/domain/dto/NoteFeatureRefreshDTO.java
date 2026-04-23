package athena.insight.biz.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "内容特征刷新请求")
public class NoteFeatureRefreshDTO {

    @Schema(description = "内容ID，传入时刷新指定内容")
    private Long noteId;

    @Schema(description = "页码，不传 noteId 时用于批量刷新公共内容池", example = "1")
    private Integer pageNum;

    @Schema(description = "每页数量，不传 noteId 时用于批量刷新公共内容池", example = "10")
    private Integer pageSize;
}
