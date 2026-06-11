package athena.rank.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

@Data
public class DigitalAssetFeedbackPageDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long total = 0L;

    private Integer current = 1;

    private Integer size = 10;

    private List<DigitalAssetFeedbackDTO> records = Collections.emptyList();
}
