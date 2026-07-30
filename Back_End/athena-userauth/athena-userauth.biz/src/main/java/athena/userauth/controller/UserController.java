package athena.userauth.controller;

import athena.athenaframework.DTO.UserDTO;
import athena.athenaframework.result.Result;
import athena.athenaframework.utils.UserIdHolder;
import athena.userauth.domain.dataobject.User;
import athena.userauth.domain.dataobject.UserAll;
import athena.userauth.service.UserService;
import jakarta.annotation.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/athena/user")
public class UserController {

    @Resource
    UserService userService;

    /**
     * 根据手机号查询用户
     * @param phone 手机号
     * @return Result<User> 统一返回结果
     */
    @GetMapping("/selectByPhone")
    public Result<User> selectByPhone(@RequestParam String phone) {
        try {
            User user = userService.selectByPhone(phone);
            if (user != null) {
                return Result.ok(user);
            } else {
                return Result.fail("未查询到该手机号对应的用户");
            }
        } catch (Exception e) {
            return Result.fail("查询用户失败：" + e.getMessage());
        }
    }

    @GetMapping("/findById")
    public Result<UserDTO> findById(@RequestParam Long id) {
        try {
            UserDTO userDTO = userService.findById(id);
            if (userDTO != null) {
                return Result.ok(userDTO);
            } else {
                return Result.ok(null);
            }
        } catch (Exception e) {
            return Result.fail("查询用户失败：" + e.getMessage());
        }
    }

    @GetMapping("/findByUserIds")
    public Result<List<UserDTO>> findByUserIds(@RequestParam List<Long> userIds) {
        try {
            List<UserDTO> userDTOList = userService.findByUserIds(userIds);
            if (userDTOList != null && !userDTOList.isEmpty()) {
                return Result.ok(userDTOList);
            } else {
                return Result.ok(List.of());
            }
        } catch (Exception e) {
            return Result.fail("批量查询用户失败：" + e.getMessage());
        }
    }

    /**
     * 更新用户头像
     * @param icon 头像路径/URL
     * @return Result<String> 统一返回结果
     */
    @PutMapping("/updateIcon")
    public Result<String> updateIconById(@RequestParam String icon) {
        try {
            boolean isSuccess = userService.updateIconById(icon);
            if (isSuccess) {
                return Result.ok("头像更新成功");
            } else {
                return Result.fail("头像更新失败，用户ID或头像信息无效");
            }
        } catch (Exception e) {
            return Result.fail("更新头像失败：" + e.getMessage());
        }
    }

    /**
     * 更新用户昵称
     * @param nickName 新昵称
     * @return Result<String> 统一返回结果
     */
    @PutMapping("/updateNickName")
    public Result<String> updateNickNameById(@RequestParam String nickName) {
        try {
            boolean isSuccess = userService.updateNickNameById(nickName);
            if (isSuccess) {
                return Result.ok("昵称更新成功");
            } else {
                return Result.fail("昵称更新失败，用户ID或昵称信息无效");
            }
        } catch (Exception e) {
            return Result.fail("更新昵称失败：" + e.getMessage());
        }
    }

    /**
     * 更新用户手机号
     * @param phone 新手机号
     * @return Result<String> 统一返回结果
     */
    @PutMapping("/updatePhone")
    public Result<String> updatePhoneById(@RequestParam String phone) {
        try {
            boolean isSuccess = userService.updatePhoneById(phone);
            if (isSuccess) {
                return Result.ok("手机号更新成功");
            } else {
                return Result.fail("手机号更新失败，用户ID或手机号信息无效");
            }
        } catch (Exception e) {
            return Result.fail("更新手机号失败：" + e.getMessage());
        }
    }

    /**
     * 获取用户全部详情信息
     * @param userId 用户ID
     * @return Result<UserAll> 统一返回结果
     */
    @GetMapping("/getUserInfo")
    public Result<UserAll> getUserInfo(@RequestParam(required = false) Long userId) {
        try {
            if (userId == null) userId = UserIdHolder.getUserId();
            UserAll userAll = userService.findUserInfoById(userId);
            if (userAll != null) {
                return Result.ok(userAll);
            } else {
                return Result.fail("未查询到该用户的详情信息");
            }
        } catch (Exception e) {
            return Result.fail("查询用户详情失败：" + e.getMessage());
        }
    }

    /**
     * 更新用户生日
     * @param birthday 新生日 (前端需传入如 "1998-05-20 " 格式)
     * @return Result<String> 统一返回结果
     */
    @PutMapping("/updateBirthday")
    public Result<String> updateBirthday(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd ") LocalDate birthday) {
        try {
            boolean isSuccess = userService.updateBirthday(birthday);
            if (isSuccess) {
                return Result.ok("生日更新成功");
            } else {
                return Result.fail("生日更新失败，用户ID或生日信息无效");
            }
        } catch (Exception e) {
            return Result.fail("更新生日失败：" + e.getMessage());
        }
    }
}
