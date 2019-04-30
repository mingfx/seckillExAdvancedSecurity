package com.ming.seckill.result;

public class CodeMsg {
    private int code;
    private String msg;

    //通用异常
    public static CodeMsg SUCCESS = new CodeMsg(0,"success");
    public static CodeMsg SERVER_ERROR = new CodeMsg(500100,"服务端异常");
    public static CodeMsg BIND_ERROR = new CodeMsg(500101,"参数校验异常:%s");
    public static CodeMsg REQUEST_ILEGAL = new CodeMsg(500102,"请求非法");
    public static CodeMsg ACCESS_LIMIT_REACHED = new CodeMsg(500103,"访问太频繁");

    //登录模块5002XX
    public static CodeMsg SESSION_ERROR = new CodeMsg(500210,"Session不存在或已失效");
    public static CodeMsg PASSWORD_EMPTY = new CodeMsg(500211,"密码不能为空");
    public static CodeMsg MOBILE_EMPTY = new CodeMsg(500212,"手机号不能为空");
    public static CodeMsg MOBILE_ERROR = new CodeMsg(500213,"手机号格式错误");
    public static CodeMsg MOBILE_NOT_EXIST = new CodeMsg(500214,"手机号不存在");
    public static CodeMsg PASSWORD_ERROR = new CodeMsg(500215,"密码错误");

    //商品模块5003XX

    //订单模块5004XX
    public static CodeMsg ORDER_NOT_EXIST = new CodeMsg(500400,"订单不存在");

    //秒杀模块5005XX
    public static CodeMsg SECKILL_RUNOUT= new CodeMsg(500500,"商品库存不足");
    public static CodeMsg SECKILL_WAIT= new CodeMsg(500555,"排队中");
    public static CodeMsg SECKILL_REPEAT = new CodeMsg(500501,"重复秒杀");
    public static CodeMsg SECKILL_VERIFYCODE_ERROR= new CodeMsg(500502,"秒杀验证码异常");
    public static CodeMsg SECKILL_VERIFYCODE_WRONG= new CodeMsg(500503,"验证码错误");


    //设为private，不希望通过构造函数来构造
    private CodeMsg(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    public CodeMsg fillArgs(Object... args){
        int code = this.code;
        String message = String.format(this.msg,args);
        return new CodeMsg(code,message);
    }

    public int getCode() {
        return code;
    }

    public String getMsg() {
        return msg;
    }
}
