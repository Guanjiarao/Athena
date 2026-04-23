package athena.insight.biz.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "推荐结果")
public class RecommendResultVO {

    @Schema(description = "推荐类型", example = "1")
    private Byte type;

    @Schema(description = "页码", example = "1")
    private Integer pageNum;

    @Schema(description = "每页数量", example = "10")
    private Integer pageSize;

    @Schema(description = "推荐内容列表")
    private List<RecommendItemVO> items;
}
