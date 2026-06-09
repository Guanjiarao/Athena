package athena.count.api;

import athena.athenaframework.result.Result;
import athena.count.api.constant.CountApiConstants;
import athena.count.api.dto.CounterDeltaDTO;
import athena.count.api.dto.CounterQueryDTO;
import athena.count.api.dto.CounterValueDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = CountApiConstants.SERVICE_NAME)
public interface CountFeignApi {

    String PREFIX = "/athena/count";

    @PostMapping(PREFIX + "/delta")
    Result<?> delta(@RequestBody CounterDeltaDTO deltaDTO);

    @GetMapping(PREFIX + "/one")
    Result<CounterValueDTO> getOne(@RequestParam String scope, @RequestParam Long targetId);

    @PostMapping(PREFIX + "/batch")
    Result<List<CounterValueDTO>> batchGet(@RequestBody CounterQueryDTO queryDTO);
}
