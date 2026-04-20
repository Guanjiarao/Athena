package athena.athenaframework.utils;

public class RedisConstants {
    public static final String LOGIN_CODE_KEY = "login:code:";
    public static final Long LOGIN_CODE_TTL = 5L;
    public static final String SMALL_TOKEN_KEY = "smallToken:";//配合id来查找小token有效期
    public static final Long SMALL_TOKEN_TTL = 1*24L;
    public static final String BIG_TOKEN_KEY = "bigToken:";//配合id来查找小token有效期
    public static final Long BIG_TOKEN_TTL = 15*24L;
    public static final Long shading=100L;

    public static final String SA_TOKEN_TOKEN_KEY_PREFIX = "Authorization:login:token:";
}
