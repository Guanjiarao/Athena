package athena.comment.biz.rpc;

import athena.athenaframework.DTO.UserDTO;
import athena.athenaframework.result.Result;
import athena.userauth.api.UserFeignApi;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Slf4j
@Component
public class UserAuthFeginApi {
    @Resource
    private UserFeignApi userFeignApi;

    public UserDTO findByUserId(Long userId)
    {
        Result<UserDTO> ru = userFeignApi.findById(userId);
        if(ru==null||ru.getCode()!=200)
        {
            return null;
        }
        return ru.getData();
    }

    public List<UserDTO> findByUserIds(List<Long> userIds)
    {
        if (userIds == null || userIds.isEmpty()) {
            // 返回空列表，避免执行无效的 SQL
            return Collections.emptyList();
            // 或者可以记录警告日志
            // log.warn("findByUserIds 传入的用户ID列表为空");
        }
        Result<List<UserDTO>> byUserIds = userFeignApi.findByUserIds(userIds);
        if(byUserIds==null||byUserIds.getCode()!=200)
        {
            return null;
        }
        return byUserIds.getData();
    }
}
