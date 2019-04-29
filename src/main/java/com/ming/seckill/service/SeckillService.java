package com.ming.seckill.service;

import com.alibaba.druid.util.StringUtils;
import com.ming.seckill.domain.OrderInfo;
import com.ming.seckill.domain.SeckillOrder;
import com.ming.seckill.domain.SeckillUser;
import com.ming.seckill.exception.GlobalException;
import com.ming.seckill.redis.OrderKey;
import com.ming.seckill.redis.RedisService;
import com.ming.seckill.redis.SeckillKey;
import com.ming.seckill.result.Result;
import com.ming.seckill.util.MD5Util;
import com.ming.seckill.util.UUIDUtil;
import com.ming.seckill.vo.SeckillGoodsVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Random;

@Service
public class SeckillService {

    @Autowired
    SeckillGoodsService seckillGoodsService;

    @Autowired
    OrderService orderService;

    @Autowired
    RedisService redisService;

    @Transactional
    public OrderInfo seckill(SeckillUser seckillUser, SeckillGoodsVo goodsVo)
            throws GlobalException{
        try {
            //减库存，原子操作
            boolean success = seckillGoodsService.reduceStock(goodsVo);
            if (success){
                //写订单(两个表）
                return orderService.createOrder(seckillUser,goodsVo);
            }else {
                setGoodsOver(goodsVo.getId());
                return null;
            }

        } catch (GlobalException e) {
            throw e;
        }
    }


    public Result<Long> getSeckillResult(Long userId, long goodsId){
//        SeckillOrder order = redisService.get(OrderKey.getSeckillOrderByUidGid,
//                userId+"_"+goodsId, SeckillOrder.class);
//        if (order!=null){
//            //success
//            return order.getId();
//        }else {
//            boolean isOver = getGoodsOver(goodsId);
//            if (isOver){
//                return -1;
//            }else {
//                return 0;
//            }
//        }

        //直接从redis中查result
        Result result = redisService.get(SeckillKey.seckillResult,userId+"_"+goodsId,Result.class);
        return result;
    }

    private boolean getGoodsOver(long goodsId) {
        //有这个key说明卖完了
        return redisService.exists(SeckillKey.isGoodsOver,""+goodsId);
    }

    private void setGoodsOver(Long goodsId) {
        redisService.set(SeckillKey.isGoodsOver,""+goodsId,true);
    }

    public void reset(List<SeckillGoodsVo> goodsList) {
//        seckillGoodsService.resetStock(goodsList);
////        orderService.deleteOrders();
    }

    public boolean checkPath(String path, Long userId, long goodsId) {
        if (userId==null||path==null){
            return false;
        }
        String pathOld = redisService.get(SeckillKey.getSeckillPath,userId+"_"+goodsId,String.class);
        return StringUtils.equals(path,pathOld);
    }

    public String createSeckillPath(Long userId, long goodsId) {
        if(userId <=0 || goodsId <=0) {
            return null;
        }
        String str = MD5Util.md5(UUIDUtil.uuid()+"123456");
        redisService.set(SeckillKey.getSeckillPath,userId+"_"+goodsId,str);
        return str;
    }

    //生成验证码图片
    public BufferedImage createSeckillVerifyCode(Long userId, long goodsId) {
        if(userId <=0 || goodsId <=0) {
            return null;
        }
        int width = 80;
        int height = 32;
        //create the image
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics g = image.getGraphics();
        // set the background color
        g.setColor(new Color(0xDCDCDC));
        g.fillRect(0, 0, width, height);
        // draw the border
        g.setColor(Color.black);
        g.drawRect(0, 0, width - 1, height - 1);
        // create a random instance to generate the codes
        Random rdm = new Random();
        // make some confusion  生成了50个干扰点
        for (int i = 0; i < 50; i++) {
            int x = rdm.nextInt(width);
            int y = rdm.nextInt(height);
            g.drawOval(x, y, 0, 0);
        }
        // generate a random code
        String verifyCode = generateVerifyCode(rdm);
        g.setColor(new Color(0, 100, 0));
        g.setFont(new Font("Candara", Font.BOLD, 24));
        g.drawString(verifyCode, 8, 24);
        g.dispose();
        //把验证码存到redis中
        int rnd = calc(verifyCode);
        redisService.set(SeckillKey.getSeckillVerifyCode,userId+","+goodsId, rnd);
        //输出图片
        return image;
    }

    public boolean checkVerifyCode(int verifyCode, Long userId, long goodsId) {
        if(userId <=0 || goodsId <=0) {
            return false;
        }
        Integer verifyCodeStore = redisService.get(SeckillKey.getSeckillVerifyCode,userId+","+goodsId,Integer.class);
        if(verifyCodeStore == null || verifyCodeStore - verifyCode != 0){
            return false;
        }
        //用完删掉
        redisService.delete(SeckillKey.getSeckillVerifyCode,userId+","+goodsId);
        return true;
    }

    //利用scriptEngine 计算结果
    private static int calc(String exp) {
        try {
            ScriptEngineManager manager = new ScriptEngineManager();
            ScriptEngine engine = manager.getEngineByName("JavaScript");
            return (Integer)engine.eval(exp);
        }catch(Exception e) {
            e.printStackTrace();
            return 0;
        }
    }

    private static char[] ops = new char[] {'+', '-', '*'};
    /**
     * + - *
     * /容易出异常
     * */
    private String generateVerifyCode(Random rdm) {
        int num1 = rdm.nextInt(10);
        int num2 = rdm.nextInt(10);
        int num3 = rdm.nextInt(10);
        char op1 = ops[rdm.nextInt(3)];
        char op2 = ops[rdm.nextInt(3)];
        String exp = ""+ num1 + op1 + num2 + op2 + num3;
        return exp;
    }



//    public static void main(String[] args) {
//        System.out.println(calc("1+3*3-5"));
//    }
}
