package com.ming.seckill.redis;

public class SeckillKey extends BasePrefix {
    //秒杀结果过期时间，这个有必要长期有效吗？似乎没有
    private static final int SECKILL_RESULT_EXPIRE = 3600;
    private static final int SECKILL_PATH_EXPIRE = 60;
    private static final int SECKILL_VerifyCode_EXPIRE = 300;
    private SeckillKey(int expireSeconds,String prefix) {
        super(expireSeconds,prefix);
    }

    public static SeckillKey isGoodsOver = new SeckillKey(0,"igo");
    public static SeckillKey seckillResult = new SeckillKey(SECKILL_RESULT_EXPIRE,"sr");
    public static SeckillKey getSeckillPath = new SeckillKey(SECKILL_PATH_EXPIRE,"gsp");
    public static SeckillKey getSeckillVerifyCode = new SeckillKey(SECKILL_VerifyCode_EXPIRE,"gsvc");

}
