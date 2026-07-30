package athena.rank.biz.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "rank")
public class RankProperties {

    private Leaderboard leaderboard = new Leaderboard();

    private DigitalAsset digitalAsset = new DigitalAsset();

    @Data
    public static class Leaderboard {

        /** 精准榜容量，超过这个名次使用粗估线段树 */
        private Integer exactCapacity = 1000;

        /** 日榜周期计算基准时间戳，单位秒 */
        private Long baseTimeSeconds = 1_762_934_400L;

        /** 同分排序未来基准时间戳，单位秒 */
        private Long tieBreakFutureSeconds = 4_102_444_800L;

        /** 幂等请求过期时间，单位秒 */
        private Long idempotentExpireSeconds = 600L;
    }

    @Data
    public static class DigitalAsset {

        /** 数字资产审核通过后单次最小发放值 */
        private Integer minScore = 1;

        /** 数字资产审核通过后单次最大发放值 */
        private Integer maxScore = 100;
    }
}
