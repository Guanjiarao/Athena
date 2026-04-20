package athena.userauth.controller;


import athena.athenaframework.result.Result;

import athena.userauth.VO.LoginFromVO;
import athena.userauth.service.LoginService;
import cn.dev33.satoken.stp.StpUtil;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;



@RestController
@RequestMapping("/athena")
public class LoginController {

    @Resource
    LoginService loginService;

    @PostMapping("/login/code")
    public Result sendCode(@RequestParam("phone") String phone) {
        return loginService.sendCode(phone);
    }
    @PostMapping("/login")
    public Result login(@RequestBody LoginFromVO loginFromVO)
    {
        return loginService.login(loginFromVO);
    }

    @GetMapping("/isLogin")
    public Result isLogin() {
        return Result.ok(StpUtil.isLogin());
    }
}
