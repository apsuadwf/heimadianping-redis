package com.hmdp.utils;

import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;

/**
 * @author XieRongji
 */
public class UserHolder {
    private static final ThreadLocal<UserDTO> TL = new ThreadLocal<>();

    public static void saveUser(UserDTO user){
        TL.set(user);
    }

    public static UserDTO getUser(){
        return TL.get();
    }

    public static void removeUser(){
        TL.remove();
    }
}
