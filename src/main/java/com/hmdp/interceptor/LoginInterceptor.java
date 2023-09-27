package com.hmdp.interceptor;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.annotation.ExcludeInterceptor;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.utils.UserHolder;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import static com.hmdp.constants.SystemConstants.*;

/**
 * 登录拦截器
 * @Author: apsuadwf
 * @Date: 2023/09/27 16:26
 */
public class LoginInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
//        // 检查是否需要排除拦截
//        if (isInterceptionExcluded(handler)) {
//            // 放行
//            return true;
//        }

        // 1. 获取session
        HttpSession session = request.getSession();
        // 2. 从session中拿到User对象
        Object user = session.getAttribute(SESSION_USER);
        // 3. 判断user是否为空
        if (user == null){
            // 4. 等于空拦截请求，设置状态码为401（“未授权”Unauthorized）
            response.setStatus(401);
            return false;
        }
        // 5. 用户存在，将用户存入ThreadLocal中
        UserHolder.saveUser((UserDTO) user);
        // 6. 放行
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
