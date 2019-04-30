package com.ming.seckill.access;

import com.alibaba.fastjson.JSON;
import com.ming.seckill.domain.SeckillUser;
import com.ming.seckill.redis.AccessKey;
import com.ming.seckill.redis.RedisService;
import com.ming.seckill.result.CodeMsg;
import com.ming.seckill.result.Result;
import com.ming.seckill.service.SeckillUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;
import org.springframework.web.util.WebUtils;
import org.thymeleaf.util.StringUtils;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.OutputStream;

@Service
public class AccessInterceptor extends HandlerInterceptorAdapter {

    @Autowired
    SeckillUserService seckillUserService;

    @Autowired
    RedisService redisService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (handler instanceof HandlerMethod){
            HandlerMethod handlerMethod = (HandlerMethod) handler;
            SeckillUser user = getSeckillUser(request,response);
            UserContext.setUser(user);
            //可以拿到方法上的注解
            AccessLimit accessLimit = handlerMethod.getMethodAnnotation(AccessLimit.class);
            if (accessLimit == null){
                return true;
            }
            int seconds = accessLimit.seconds();
            int maxCount = accessLimit.maxCount();
            boolean needLogin = accessLimit.needLogin();
            String key = request.getRequestURI();
            if (needLogin){
                if (user == null){
                    render(response, CodeMsg.SESSION_ERROR);
                    return false;
                }
                key = key + "_" + user.getId();
            }

            AccessKey accessKey = AccessKey.withExpire(seconds);
            Integer count = redisService.get(accessKey,key,Integer.class);
            if (count==null){
                redisService.set(accessKey,key,1);
            }else if (count < maxCount){
                redisService.incr(accessKey,key);
            }else{
                render(response,CodeMsg.ACCESS_LIMIT_REACHED);
                return false;
            }
            return true;
        }
        return true;
    }

    private void render(HttpServletResponse response, CodeMsg cm) throws Exception{
        //通过输出流把错误写出去
        response.setContentType("application/json;charset=UTF-8");
        OutputStream out = response.getOutputStream();
        String str = JSON.toJSONString(Result.error(cm));
        out.write(str.getBytes("UTF-8"));
        out.flush();
        out.close();
    }

    private SeckillUser getSeckillUser(HttpServletRequest request, HttpServletResponse response){
        String paramToken = request.getParameter(SeckillUserService.COOKIE_NAME_TOKEN);
        String cookieToken = getCookieValue(request,SeckillUserService.COOKIE_NAME_TOKEN);
        if (StringUtils.isEmpty(cookieToken)&& StringUtils.isEmpty(paramToken)){
            return null;
        }
        String token = StringUtils.isEmpty(paramToken)?cookieToken:paramToken;
        SeckillUser seckillUser = seckillUserService.getByToken(response,token);
        return seckillUser;
    }

    private String getCookieValue(HttpServletRequest request, String cookieNameToken) {
        Cookie[] cookies = request.getCookies();
        if (cookies==null||cookies.length<=0){
            return null;
        }
        for (Cookie cookie:
                cookies) {
            if (cookie.getName().equals(cookieNameToken)){
                return cookie.getValue();
            }
        }
        return null;
    }
}
