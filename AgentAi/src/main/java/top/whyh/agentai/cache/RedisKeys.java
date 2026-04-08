package top.whyh.agentai.cache;


public class RedisKeys {

    /**
     * 短信验证码 Key
     */
    public static String getSmsCodeKey(String mobile) {
        return "sms:code:" + mobile;
    }

    /**
     * 用户 Token Key（支持 String 类型的 userId）
     */
    public static String getUserTokenKey(String userId) {
        return "user:token:" + userId;
    }
}
