package athena.rank.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class DigitalAssetAccountDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long userId;

    private Integer totalAsset;

    private LocalDateTime updateTime;
}
