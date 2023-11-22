package com.hmdp.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 登录表单对象
 * @author XieRongji
 */
@Data
@Builder
public class LoginFormDTO {
    private String phone;
    private String code;
    private String password;
}
