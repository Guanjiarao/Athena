package athena.userauth.api;


import athena.athenaframework.DTO.UserDTO;
import athena.athenaframework.result.Result;
import athena.userauth.constant.ApiConstants;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = ApiConstants.SERVICE_NAME)
public interface UserFeignApi {
    String PREFIX = "/athena/user";

    @GetMapping(value = PREFIX + "/findById")
    Result<UserDTO> findById(@RequestParam Long id);

    @GetMapping(value = PREFIX + "/findByUserIds")
    Result<List<UserDTO>> findByUserIds(@RequestParam List<Long> userIds);
}
