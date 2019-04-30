package com.ming.seckill.controller;

import com.ming.seckill.access.AccessLimit;
import com.ming.seckill.domain.OrderInfo;
import com.ming.seckill.domain.SeckillGoods;
import com.ming.seckill.domain.SeckillOrder;
import com.ming.seckill.domain.SeckillUser;
import com.ming.seckill.rabbitMQ.MQSender;
import com.ming.seckill.rabbitMQ.SeckillMessage;
import com.ming.seckill.redis.*;
import com.ming.seckill.result.CodeMsg;
import com.ming.seckill.result.Result;
import com.ming.seckill.service.OrderService;
import com.ming.seckill.service.SeckillGoodsService;
import com.ming.seckill.service.SeckillService;
import com.ming.seckill.util.MD5Util;
import com.ming.seckill.util.UUIDUtil;
import com.ming.seckill.vo.SeckillGoodsVo;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import javax.imageio.ImageIO;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.awt.color.CMMException;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/seckill")
public class SeckillController implements InitializingBean {
    //实现initializingBean从而在初始化时将库存加载
    @Autowired
    SeckillGoodsService seckillGoodsService;

    @Autowired
    OrderService orderService;

    @Autowired
    SeckillService seckillService;

    @Autowired
    RedisService redisService;

    @Autowired
    MQSender sender;

    private Map<Long,Boolean> lcoalOverMap = new HashMap<>();

    @Override
    public void afterPropertiesSet() throws Exception {
        List<SeckillGoodsVo> goodsList = seckillGoodsService.getListSeckillGoodsVo();
        if (goodsList==null){
            return;
        }
        for (SeckillGoodsVo good :
                goodsList) {
            redisService.set(GoodsKey.getSeckillGoodsStock,""+good.getId(),good.getStockCount());
            lcoalOverMap.put(good.getId(),false);
        }

    }
    //get post有什么区别：真正区别：get幂等（获取数据，调用多少次都是一样的）post（非幂等，提交数据
    //优化：直接返回订单。为啥要这么做呢？不是应该避免返回reslut这些信息吗
    @PostMapping("/{path}/do_seckill")
    @ResponseBody
    public Result<Integer> doSeckill(SeckillUser seckillUser,
                            @PathVariable("path") String path,
                            @RequestParam("goodsId") long goodsId){
        if (seckillUser==null){
            return Result.error(CodeMsg.SESSION_ERROR);
        }
        //验证path
        boolean checkPath = seckillService.checkPath(path,seckillUser.getId(),goodsId);
        if (!checkPath){
            return Result.error(CodeMsg.REQUEST_ILEGAL);
        }
        //内存标记，为了进一步减少网络开销，将库存不够的直接存在内存中，redis也不要访问
        boolean over = lcoalOverMap.get(goodsId);
        if (over){
            return Result.error(CodeMsg.SECKILL_RUNOUT);
        }
        //预减库存，减一，返回结果值
        long stock = redisService.decr(GoodsKey.getSeckillGoodsStock,""+goodsId);
        if (stock < 0){
            lcoalOverMap.put(goodsId,true);
            return Result.error(CodeMsg.SECKILL_RUNOUT);
        }
        //判断是否已经秒杀过了(做了优化，查缓存里的订单）
        SeckillOrder seckillOrder = orderService.getSeckillOrderByUserIdGoodsId(seckillUser.getId(),goodsId);
        if (seckillOrder!=null){
            //重复秒杀，返回失败信息
            return Result.error(CodeMsg.SECKILL_REPEAT);
        }
        //入队
        redisService.set(SeckillKey.seckillResult,seckillUser.getId()+"_"+goodsId,Result.seckillWait());
        SeckillMessage message = new SeckillMessage();
        message.setSeckillUser(seckillUser);
        message.setGoodsId(goodsId);
        sender.sendSeckillMessage(message);
        return Result.seckillWait();//排队中
        /*
         * 优化之前的
        //判断库存（还没优化？
        SeckillGoodsVo goodsVo = seckillGoodsService.getSeckillGoodsVoById(goodsId);
        int stock = goodsVo.getStockCount();
        if (stock<0){
            //库存不足，返回失败信息
            return Result.error(CodeMsg.SECKILL_OVER);
        }
        //判断是否已经秒杀过了(做了优化，查缓存里的订单）
        SeckillOrder seckillOrder = orderService.getSeckillOrderByUserIdGoodsId(seckillUser.getId(),goodsId);
        if (seckillOrder!=null){
            //重复秒杀，返回失败信息
            return Result.error(CodeMsg.SECKILL_REPEAT);
        }
        //秒杀,成功返回订单详情
        OrderInfo orderInfo = seckillService.seckill(seckillUser,goodsVo);
        return Result.success(orderInfo);
         */
    }

    /**
     *orderId：成功
     * 500555：排队中
     */
    @GetMapping("/result")
    @ResponseBody
    public Result<Long> getSeckillResult(Model model,
                                     SeckillUser seckillUser,
                                     @RequestParam("goodsId") long goodsId) {
        if (seckillUser == null) {
            return Result.error(CodeMsg.SESSION_ERROR);
        }
        //long result = seckillService.getSeckillResult(seckillUser.getId(),goodsId);
        Result<Long> result = seckillService.getSeckillResult(seckillUser.getId(),goodsId);
        return result;
    }

    @AccessLimit(seconds = 5,maxCount = 5,needLogin = true)
    @GetMapping("/path")
    @ResponseBody
    public Result<String> getSeckillPath(HttpServletRequest request,
                                         SeckillUser seckillUser,
                                         @RequestParam("goodsId") long goodsId,
                                         @RequestParam(value = "verifyCode",defaultValue = "0") int verifyCode) {
        if (seckillUser == null) {
            return Result.error(CodeMsg.SESSION_ERROR);
        }
        //限流防刷，查询访问次数。5秒钟访问5次  放在拦截器里了
//        String uri = request.getRequestURI();
//        //限定同一用户访问一个接口的次数
//        String key = uri + "_" +seckillUser.getId();
//        Integer count = redisService.get(AccessKey.access,key,Integer.class);
//        if (count==null){
//            redisService.set(AccessKey.access,key,1);
//        }else if (count < 5){
//            redisService.incr(AccessKey.access,key);
//        }else{
//            return Result.error(CodeMsg.ACCESS_LIMIT_REACHED);
//        }

        //验证码校验
        boolean check = seckillService.checkVerifyCode(verifyCode,seckillUser.getId(),goodsId);
        if (!check){
            return Result.error(CodeMsg.SECKILL_VERIFYCODE_WRONG);
        }
        String path = seckillService.createSeckillPath(seckillUser.getId(),goodsId);
        return Result.success(path);
    }

    //生成验证码
    @GetMapping("/verifyCode")
    @ResponseBody
    public Result<String> getSeckillVerifyCode(HttpServletResponse response,
                                               SeckillUser seckillUser,
                                               @RequestParam("goodsId") long goodsId) {
        if (seckillUser == null) {
            return Result.error(CodeMsg.SESSION_ERROR);
        }
        BufferedImage image = seckillService.createSeckillVerifyCode(seckillUser.getId(),goodsId);
        try {
            //把图片通过输出流返回
            OutputStream out = response.getOutputStream();
            ImageIO.write(image,"JPEG",out);
            out.flush();
            out.close();
            return null;
        } catch (IOException e) {
            e.printStackTrace();
            return Result.error(CodeMsg.SECKILL_VERIFYCODE_ERROR);
        }
    }

    //reset 正常应该是通过管理后台来设置
    @RequestMapping(value="/reset", method=RequestMethod.GET)
    @ResponseBody
    public Result<Boolean> reset(Model model) {
        List<SeckillGoodsVo> goodsList = seckillGoodsService.getListSeckillGoodsVo();
        for(SeckillGoodsVo goods : goodsList) {
            goods.setStockCount(10);
            redisService.set(GoodsKey.getSeckillGoodsStock, ""+goods.getId(), 10);
            lcoalOverMap.put(goods.getId(), false);
        }
//        redisService.delete(OrderKey.getSeckillOrderByUidGid,);
//        redisService.delete(SeckillKey.seckillResult,);
        seckillService.reset(goodsList);
        return Result.success(true);
    }
}
