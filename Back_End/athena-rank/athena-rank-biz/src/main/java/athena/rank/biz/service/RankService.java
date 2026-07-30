package athena.rank.biz.service;

import athena.rank.api.dto.RankListDTO;
import athena.rank.api.dto.RankPositionDTO;
import athena.rank.api.dto.RankQueryDTO;
import athena.rank.api.dto.RankUpdateDTO;

public interface RankService {

    void update(RankUpdateDTO updateDTO);

    RankListDTO top(RankQueryDTO queryDTO);

    RankPositionDTO position(String scene, Long memberId, Long periodTimeMillis);
}
