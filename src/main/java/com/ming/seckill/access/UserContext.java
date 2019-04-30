package com.ming.seckill.access;

import com.ming.seckill.domain.SeckillUser;

public class UserContext {
    private static ThreadLocal<SeckillUser> userHolder = new ThreadLocal<>();

    public static void setUser(SeckillUser user){
        userHolder.set(user);
    }

    public static SeckillUser getSeckillUser() {
        return userHolder.get();
    }
}
