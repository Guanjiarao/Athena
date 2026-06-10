package athena.count.biz.service;

import athena.count.api.dto.CounterBatchDeltaDTO;
import athena.count.api.dto.CounterDeltaDTO;
import athena.count.api.dto.CounterQueryDTO;
import athena.count.api.dto.CounterValueDTO;

import java.util.List;

public interface CountService {

    void delta(CounterDeltaDTO deltaDTO);

    void batchDelta(CounterBatchDeltaDTO batchDeltaDTO);

    void applyDelta(String scope, Long targetId, String counterType, Long delta);

    CounterValueDTO getOne(String scope, Long targetId);

    List<CounterValueDTO> batchGet(CounterQueryDTO queryDTO);

    void deleteTarget(String scope, Long targetId);
}
