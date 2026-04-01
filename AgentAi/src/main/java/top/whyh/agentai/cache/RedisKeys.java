package top.whyh.agentai.cache;


public class RedisKeys {

    /**
     * 短信验证码 Key
     */
    public static String getSmsCodeKey(String mobile) {
        return "sms:code:" + mobile;
    }

    /**
     * 用户 Token Key
     */
    public static String getUserTokenKey(Long userId) {
        return "user:token:" + userId;
    }
}
