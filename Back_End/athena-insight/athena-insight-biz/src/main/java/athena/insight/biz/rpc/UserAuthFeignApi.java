package athena.insight.biz.rpc;

import athena.athenaframework.DTO.UserDTO;
import athena.athenaframework.result.Result;
import athena.userauth.api.UserFeignApi;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class UserAuthFeignApi {

    @Resource
    private UserFeignApi userFeignApi;

    public UserDTO findByUserId(Long userId) {
        Result<UserDTO> result = userFeignApi.findById(userId);
        if (result == null || result.getCode() != 200) {
            log.warn("[UserAuthFeignApi] 查询用户失败, userId={}", userId);
            return null;
        }
        return result.getData();
    }

    public List<UserDTO> findByUserIds(List<Long> userIds) {
        Result<List<UserDTO>> result = userFeignApi.findByUserIds(userIds);
        if (result == null || result.getCode() != 200) {
            log.warn("[UserAuthFeignApi] 批量查询用户失败, userIds={}", userIds);
            return null;
        }
        return result.getData();
    }
}
