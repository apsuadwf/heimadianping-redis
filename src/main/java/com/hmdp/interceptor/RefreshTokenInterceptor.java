package com.hmdp.interceptor;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.constants.SystemConstants.AUTHORIZATION;
import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;
import static com.hmdp.utils.RedisConstants.LOGIN_USER_TTL;

/**
 * 刷新Token拦截器
 *
 * @Author: apsuadwf
 * @Date: 2023/11/10 10:17
 */
@Slf4j
public class RefreshTokenInterceptor implements HandlerInterceptor {

    private StringRedisTemplate redisTemplate;

    public RefreshTokenInterceptor(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1. 获取请求头中的token
        String token = request.getHeader(AUTHORIZATION);
        //log.info("Authorization---->{}",token);
        if (StrUtil.isBlank(token)) {
            return true;
        }
        // 2. 从redis中拿到User对象
        String tokenKey = LOGIN_USER_KEY + token;
        Map<Object, Object> userMap = redisTemplate.opsForHash().entries(tokenKey);
        // 3. 判断user是否为空
        if (userMap.isEmpty()){
            return true;
        }
        // 5. 用户存在，将用户存入ThreadLocal中
        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
        UserHolder.saveUser(userDTO);

        // 刷新token有效期
        //redisTemplate.expire(tokenKey,LOGIN_USER_TTL, TimeUnit.MINUTES);
        // 6. 放行
        return true;
    }
}
