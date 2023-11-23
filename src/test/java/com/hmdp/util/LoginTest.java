package com.hmdp.util;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.service.impl.UserServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * @Author: apsuadwf
 * @Date: 2023/11/22 15:35
 */
@SpringBootTest
public class LoginTest {
    @Autowired
    private UserServiceImpl userService;

    @Autowired
    private StringRedisTemplate redisTemplate;
    @Test
    void randomPhoneLoginTest(){
        String prefix = "1914204";
        URL resource = LoginTest.class.getClassLoader().getResource("tokens.txt");
        try (PrintWriter writer = new PrintWriter(new FileWriter(new File(resource.toURI())))) {
            for (int i = 0; i < 1000; i++) {

                String phone = prefix + StrUtil.padPre(String.valueOf(i), 4, '0');
                String token = UUID.randomUUID().toString(true);
                User user = userService.lambdaQuery().eq(User::getPhone, phone).one();
                // 将User对象转换成HashMap存储
                UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
                Map<String, Object> userMap = BeanUtil.beanToMap(userDTO,new HashMap<>(8),
                        CopyOptions.create().setIgnoreNullValue(true).setFieldValueEditor((fieldName, fieldValue)->fieldValue.toString()));
                // 存储
                String tokenKey = LOGIN_USER_KEY + token;
                redisTemplate.opsForHash().putAll(tokenKey,userMap);
                // 将 token 写入文件
                writer.println(token);
            }
        } catch (IOException | URISyntaxException e) {
            e.printStackTrace();
        }
    }
}
