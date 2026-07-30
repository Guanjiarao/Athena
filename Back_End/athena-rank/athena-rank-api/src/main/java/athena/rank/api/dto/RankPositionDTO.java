package athena.rank.api.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class RankPositionDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String scene;

    private Long memberId;

    private Long periodNo;

    private Long rankNo;

    private Long score;

    private Boolean estimated;
}
