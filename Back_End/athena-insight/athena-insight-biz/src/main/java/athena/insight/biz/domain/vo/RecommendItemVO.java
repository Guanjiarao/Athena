package athena.insight.biz.domain.vo;

import lombok.Data;

import java.util.List;

@Data
public class RecommendItemVO {

    private Long noteId;

    private Byte type;

    private String title;

    private String coverUrl;

    private Long authorId;

    private List<String> topics;

    private String reason;

    private Double score;
}
