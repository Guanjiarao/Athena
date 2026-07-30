package athena.rank.api.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class RankUpdateDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 排行榜场景，例如 data_asset_daily、newcomer_daily */
    private String scene;

    /** 业务实体 ID。数据资产榜可以是资产 ID，新人榜可以是用户 ID */
    private Long memberId;

    /** 本次积分增量 */
    private Long delta;

    /** 分布式唯一请求 ID，用于幂等 */
    private String requestId;

    /** 业务发生时间毫秒时间戳。为空时使用服务器当前时间 */
    private Long eventTimeMillis;
}
