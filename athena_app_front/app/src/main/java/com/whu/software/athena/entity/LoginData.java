package com.whu.software.athena.entity;

/**
 * 登录接口 data 字段对应的实体类。
 *
 * 后端真实返回：
 * {
 *   "code": 200,
 *   "message": "成功",
 *   "data": {
 *     "token": "xxx",
 *     "firsrLogin": true   ← 后端拼写错误，此处手动映射
 *   }
 * }
 */
public class LoginData {

    private String  token;

    /**
     * 后端字段名拼错为 "firsrLogin"，前端变量保持正确拼写 firstLogin。
     * 因项目未引入 Gson，直接在 JSON 解析层用 optBoolean("firsrLogin") 处理，
     * 此处字段仅用于承载解析结果。
     */
    private boolean firstLogin;

    public LoginData(String token, boolean firstLogin) {
        this.token      = token;
        this.firstLogin = firstLogin;
    }

    public String  getToken()      { return token; }
    public boolean isFirstLogin()  { return firstLogin; }
}
