package athena.userauth.service;


import athena.athenaframework.result.Result;
import athena.userauth.VO.LoginFromVO;



public interface LoginService {

    Result sendCode(String phone);

    Result login(LoginFromVO loginForm);


}
