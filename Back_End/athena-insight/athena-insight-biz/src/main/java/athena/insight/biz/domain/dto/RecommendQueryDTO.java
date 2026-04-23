package athena.insight.biz.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "推荐查询请求")
public class RecommendQueryDTO {

    @Schema(description = "推荐类型", example = "1")
    private Byte type;

    @Schema(description = "频道ID，可选", example = "1001")
    private Integer channelId;

    @Schema(description = "页码", example = "1")
    private Integer pageNum;

    @Schema(description = "每页数量", example = "10")
    private Integer pageSize;
}
