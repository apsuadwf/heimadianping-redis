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
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    public Result sendCode(String phone) {
        // 1. 验证手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            // 2. 如果不符合，返回错误信息
            return Result.fail("手机号格式错误");
        }
        // 3. 符合，生成验证码
        String code = RandomUtil.randomNumbers(6);
        // 4. 保存验证码到session中
        // session.setAttribute(CODE,code)
        // set login:code:{phone}
        redisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);
        // 4. 发送验证码
        log.debug("发送短信验证码成功，验证码：{}", code);
        // 5. 返回ok
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm) {
        // 1. 校验手机号
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            // 2. 如果不符合，返回错误信息
            return Result.fail("手机号格式错误");
        }
        // 3. 验证短信验证码
        String code = loginForm.getCode();
        // 从redis中获取验证码
        String cacheCode = redisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        if (!code.equals(cacheCode)) {
            // 4. 如果不符合，返回验证码错误
            return Result.fail("验证码错误");
        }
        // 6. 查询用户是否存在
        /*
            query().eq("phone",loginForm.getPhone()).one()
         */
        User user = lambdaQuery().eq(User::getPhone, loginForm.getPhone()).one();
        if (user == null) {
            // 7.用户不存在，创建用户
            user = createUserWithPhone(phone);
        }
        // 8.保存用户信息到redis中
        // 随机生成token,作为登录令牌
        String token = UUID.randomUUID().toString(true);
        // 将User对象转换成HashMap存储
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(8),
                CopyOptions.create().setIgnoreNullValue(true).setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));
        log.info("userDto->{}", userMap);
        // 存储
        String tokenKey = LOGIN_USER_KEY + token;
        redisTemplate.opsForHash().putAll(tokenKey, userMap);
        redisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.MINUTES);
        return Result.ok(token);
    }

    @Override
    public Result sign() {
        // 获取当前登录用户
        Long userId = UserHolder.getUser().getId();
        // 获取日期
        LocalDate currentDate = LocalDate.now();
        String keySuffix = currentDate.format(DateTimeFormatter.ofPattern(":yyyy:MM"));
        // 拼接key
        String key = USER_SIGN_KEY + userId + keySuffix;
        // 获取今天是本月的第几天
        // from 1 to 31,bitMap从0开始,所以减一
        int dayOfMonth = currentDate.getDayOfMonth();
        // 写入redis setbit key offset 1
        redisTemplate.opsForValue().setBit(key, dayOfMonth - 1, true);
        return Result.ok();
    }

    @Override
    public Result signCount() {
        // 获取当前用户
        Long userId = UserHolder.getUser().getId();
        // 获取当前日期
        LocalDate currentDate = LocalDate.now();
        String keySuffix = currentDate.format(DateTimeFormatter.ofPattern(":yyyy:MM"));
        // 拼接key
        String key = USER_SIGN_KEY + userId + keySuffix;
        // 获取今天是本月的第几天
        int dayOfMonth = currentDate.getDayOfMonth();
        // 获取截止当前日期的本月的所有签到情况
        List<Long> result = redisTemplate.opsForValue().bitField(key,
                BitFieldSubCommands.create()
                        // 从0开始取当前天数位
                        .get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0));
        if (Objects.isNull(result) || result.isEmpty()) {
            // 没有任何签到结果
            return Result.ok(0);
        }
        Long num = result.get(0);
        if (Objects.isNull(num) || num == 0) {
            return Result.ok(0);
        }
        // 循环遍历
        int count = 0;
        while (true) {
            // 先让这个数组与1做与运算,得到数字的最后一个bit位
            // 判断这个bit位是否为0
            if ((num & 1) == 0) {
                // 如果为0,说明未签到,结束
                break;
            }else {
                // 如果不为0,说明已签到,计数器+1
                count++;
            }
            // 把数字右移一位,抛弃最后一位bit位,继续下一个bit位
            num>>>=1;
        }
        return Result.ok(count);
    }

    /**
     * 根据手机号创建用户
     *
     * @param phone 用户手机号
     * @return 填充好字段的User对象
     */
    private User createUserWithPhone(String phone) {
        // 1. 创建User对象
        User user = new User();
        // 2. 填充电话
        user.setPhone(phone);
        // 3. 填充昵称
        user.setNickName(phone + USER_NICK_NAME_SUFFIX);
        // 4. 插入数据库
        save(user);
        return user;
    }
}
