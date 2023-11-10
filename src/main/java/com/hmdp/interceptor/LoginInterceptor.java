package com.hmdp.interceptor;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.annotation.ExcludeInterceptor;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.constants.SystemConstants.*;
import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;
import static com.hmdp.utils.RedisConstants.LOGIN_USER_TTL;

/**
 * 登录拦截器
 * @Author: apsuadwf
 * @Date: 2023/09/27 16:26
 */
@Slf4j
public class LoginInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        /*
            // 检查是否需要排除拦截
            if (isInterceptionExcluded(handler)) {
            // 放行
            return true;
            }
        */
       // 判断是否需要拦截(ThreadLocal 中是否有用户）
        if (UserHolder.getUser() == null){
            // 4. 等于空拦截请求，设置状态码为401（“未授权”Unauthorized）
            response.setStatus(401);
            return false;
        }
        // 用户存在，放行
        return true;
    }
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // 请求响应完成后执行，并删除ThreadLocal中的数据防止内存溢出
        UserHolder.removeUser();
    }

    /**
     * 检查是否需要排除拦截的请求。
     *
     * @param handler 处理器对象，通常是一个 HandlerMethod，用于表示正在处理的请求方法。
     * @return 如果需要排除拦截，返回 true；否则返回 false。
     */
    private boolean isInterceptionExcluded(Object handler){
        if (handler instanceof HandlerMethod) {
            HandlerMethod handlerMethod = (HandlerMethod) handler;
            if (handlerMethod.hasMethodAnnotation(ExcludeInterceptor.class) || handlerMethod.getBeanType().isAnnotationPresent(ExcludeInterceptor.class)) {
                // 存在自定义注解，跳过拦截
                return true;
            }
        }
        return false;
    }
}
