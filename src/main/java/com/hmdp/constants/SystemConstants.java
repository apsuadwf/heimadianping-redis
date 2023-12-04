package com.hmdp.constants;

/**
 * 系统常量
 * @author XieRongji
 */
public class SystemConstants {
    public static final String AUTHORIZATION = "Authorization";

    public static final String IMAGE_UPLOAD_DIR = "D:\\Learn_Files\\Redis\\nginx-1.18.0\\html\\hmdp\\imgs";
    /**
     * 用户昵称前缀
     */
    public static final String USER_NICK_NAME_PREFIX = "user_";
    /**
     * 昵称后缀
     */
    public static final String USER_NICK_NAME_SUFFIX = "用户";
    /**
     * Session中存储的用户标识
     */
    public static final String SESSION_USER= "SESSION_USER";

    /**
     * 验证码常量
     */
    public static final String CODE = "CODE";
    public static final int DEFAULT_PAGE_SIZE = 5;
    public static final int MAX_PAGE_SIZE = 10;
    /**
     * 最大重试次数
     */
    public static final int MAX_RETRY_COUNT = 200;
}
