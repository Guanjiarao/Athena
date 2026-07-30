package athena.rank.api.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class RankQueryDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String scene;

    /** 查询周期内时间点。为空时使用服务器当前时间 */
    private Long periodTimeMillis;

    /** 0-based 起始下标 */
    private Integer start;

    /** 返回数量 */
    private Integer size;
}
