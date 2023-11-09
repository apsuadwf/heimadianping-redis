package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;

import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import javax.servlet.http.HttpSession;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.constants.SystemConstants.*;
import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        // 1. 验证手机号
        if (RegexUtils.isPhoneInvalid(phone)){
            // 2. 如果不符合，返回错误信息
            return Result.fail("手机号格式错误");
        }
        // 3. 符合，生成验证码
        String code = RandomUtil.randomNumbers(6);
        // 4. 保存验证码到session中
        // session.setAttribute(CODE,code)
        // set login:code:{phone}
        redisTemplate.opsForValue().set(LOGIN_CODE_KEY +phone,code,LOGIN_CODE_TTL, TimeUnit.MINUTES);
        // 4. 发送验证码
        log.debug("发送短信验证码成功，验证码：{}",code);
        // 5. 返回ok
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        // 1. 校验手机号
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)){
            // 2. 如果不符合，返回错误信息
            return Result.fail("手机号格式错误");
        }
        // 3. 验证短信验证码
        String code = loginForm.getCode();
        // 从redis中获取验证码
        String cacheCode = redisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        if (!code.equals(cacheCode)){
            // 4. 如果不符合，返回验证码错误
            return Result.fail("验证码错误");
        }
        // 6. 查询用户是否存在
        /*
            query().eq("phone",loginForm.getPhone()).one()
         */
        User user = lambdaQuery().eq(User::getPhone, loginForm.getPhone()).one();
        if (user == null){
            // 7.用户不存在，创建用户
            user = createUserWithPhone(phone);
        }
        // 8.保存用户信息到redis中
        // 随机生成token,作为登录令牌
        String token = UUID.randomUUID().toString(true);
        // 将User对象转换成HashMap存储
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO,new HashMap<>(8),
                CopyOptions.create().setIgnoreNullValue(true).setFieldValueEditor((fieldName,fieldValue)->fieldValue.toString()));
        log.info("userDto->{}",userMap);
        // 存储
        String tokenKey = LOGIN_USER_KEY + token;
        redisTemplate.opsForHash().putAll(tokenKey,userMap);
        redisTemplate.expire(tokenKey,LOGIN_USER_TTL,TimeUnit.MINUTES);
        return Result.ok(token);
    }

    /**
     * 根据手机号创建用户
     * @param phone 用户手机号
     * @return 填充好字段的User对象
     */
    private User createUserWithPhone(String phone) {
        // 1. 创建User对象
        User user = new User();
        // 2. 填充电话
        user.setPhone(phone);
        // 3. 填充昵称
        user.setNickName(phone+ USER_NICK_NAME_SUFFIX);
        // 4. 插入数据库
        save(user);
        return user;
    }
}
