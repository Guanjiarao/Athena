package athena.insight.biz.domain.vo;

import lombok.Data;

import java.util.List;

@Data
public class RecommendResultVO {

    private Byte type;

    private Integer pageNum;

    private Integer pageSize;

    private List<RecommendItemVO> items;
}
