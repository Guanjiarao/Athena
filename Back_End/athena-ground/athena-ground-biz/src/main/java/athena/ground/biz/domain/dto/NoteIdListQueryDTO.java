package athena.ground.biz.domain.dto;

import lombok.Data;

import java.util.List;

@Data
public class NoteIdListQueryDTO {
    private List<Long> noteIdList;
}
