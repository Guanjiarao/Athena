package athena.rank.api.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class RankItemDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long memberId;

    /** 展示排名，从 1 开始 */
    private Long rankNo;

    /** 真实积分，不包含同分排序用的小数时间因子 */
    private Long score;

    /** Redis ZSET 内部排序分数 */
    private Double rawScore;

    /** 是否来自粗估线段树 */
    private Boolean estimated;
}
