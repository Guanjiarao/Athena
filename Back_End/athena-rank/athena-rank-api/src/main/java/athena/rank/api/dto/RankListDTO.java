package athena.rank.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class RankListDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String scene;

    private Long periodNo;

    private List<RankItemDTO> items;
}
