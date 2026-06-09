package athena.count.biz.controller;

import athena.athenaframework.result.Result;
import athena.count.api.CountFeignApi;
import athena.count.api.dto.CounterDeltaDTO;
import athena.count.api.dto.CounterQueryDTO;
import athena.count.api.dto.CounterValueDTO;
import athena.count.biz.service.CountService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class CountController implements CountFeignApi {

    @Resource
    private CountService countService;

    @Override
    public Result<?> delta(CounterDeltaDTO deltaDTO) {
        countService.delta(deltaDTO);
        return Result.ok();
    }

    @Override
    public Result<CounterValueDTO> getOne(String scope, Long targetId) {
        return Result.ok(countService.getOne(scope, targetId));
    }

    @Override
    public Result<List<CounterValueDTO>> batchGet(CounterQueryDTO queryDTO) {
        return Result.ok(countService.batchGet(queryDTO));
    }
}
