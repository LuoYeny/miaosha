package com.wp.miaoshaproject.controller;

import com.google.common.util.concurrent.RateLimiter;
import com.wp.miaoshaproject.error.BusinessException;
import com.wp.miaoshaproject.error.EmBusinessError;
import com.wp.miaoshaproject.mq.MqProducer;
import com.wp.miaoshaproject.response.CommonReturnType;
import com.wp.miaoshaproject.service.ItemService;
import com.wp.miaoshaproject.service.OrderService;
import com.wp.miaoshaproject.service.PromoService;
import com.wp.miaoshaproject.service.model.OrderModel;
import com.wp.miaoshaproject.service.model.UserModel;

import com.wp.miaoshaproject.utill.CodeUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.annotation.PostConstruct;
import javax.imageio.ImageIO;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.awt.image.RenderedImage;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.*;

import static com.wp.miaoshaproject.error.EmBusinessError.PARAMETER_VALIDATION_ERROR;

/**
 * @author WangPan wangpanhust@qq.com
 * @date 2019/6/10 19:18
 * @description 处理前端对订单的请求
 **/
@Controller("order")
@RequestMapping("/order")
//跨域请求，保证session发挥作用
@CrossOrigin(allowCredentials = "true",allowedHeaders = "*")
public class OrderController extends BaseController{

    //public static final String CONTENT_TYPE_FORMED="application/x-www-form-urlencoded";

    @Autowired
    private OrderService orderService;

    @Autowired
    private HttpServletRequest httpServletRequest;

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private MqProducer mqProducer;
    @Autowired
    private ItemService itemService;
    @Autowired
    private PromoService promoService;

    private ExecutorService executorService;

    private RateLimiter orderCreateRateLimiter;
    @PostConstruct
    public void init(){
        executorService=  Executors.newFixedThreadPool(20);
        orderCreateRateLimiter=RateLimiter.create(100);//令牌桶 限流5

    }
    //生成验证码
    @RequestMapping(value = "/generateverifycode",method = {RequestMethod.GET,RequestMethod.POST})
    @ResponseBody
    public void generateverifycode(HttpServletResponse response) throws BusinessException, IOException {
        String token = httpServletRequest.getParameterMap().get("token")[0];
        if(org.springframework.util.StringUtils.isEmpty(token)){
            throw new BusinessException(EmBusinessError.USER_NOT_LONGIN,"用户还未登陆，不能生成验证码");
        }
        UserModel userModel = (UserModel) redisTemplate.opsForValue().get(token);
        if(userModel == null){
            throw new BusinessException(EmBusinessError.USER_NOT_LONGIN,"用户还未登陆，不能生成验证码");
        }

        Map<String,Object> map = CodeUtil.generateCodeAndPic();

        redisTemplate.opsForValue().set("verify_code_"+userModel.getId(),map.get("code"));
        redisTemplate.expire("verify_code_"+userModel.getId(),10,TimeUnit.MINUTES);

        ImageIO.write((RenderedImage) map.get("codePic"), "jpeg", response.getOutputStream());


    }
    @RequestMapping(value = "/generatetoken", method = {RequestMethod.POST}, consumes = {CONTENT_TYPE_FORMED})
    @ResponseBody
    public CommonReturnType generatetoken(@RequestParam(name = "itemId") Integer itemId,
                                        @RequestParam(name = "promoId", required = false) Integer promoId) throws BusinessException {

        String token = httpServletRequest.getParameterMap().get("token")[0];
        if(StringUtils.isEmpty(token)){
            throw new BusinessException(PARAMETER_VALIDATION_ERROR, "用户还未登录，无法下单");
        }
        UserModel userModel = (UserModel) redisTemplate.opsForValue().get(token);
        if(userModel==null) {
            throw new BusinessException(PARAMETER_VALIDATION_ERROR, "用户还未登录，无法下单");
        }
        //获取秒杀访问令牌
        String promoToken = promoService.generateSecondKillToken(promoId,itemId,userModel.getId());
        if(promoToken==null){
            throw new BusinessException(PARAMETER_VALIDATION_ERROR,"生成秒杀令牌失败");
        }
        return CommonReturnType.create(promoId);
    }
    @RequestMapping(value = "/createorder", method = {RequestMethod.POST}, consumes = {CONTENT_TYPE_FORMED})
    @ResponseBody
    public CommonReturnType createOrder(@RequestParam(name = "itemId") Integer itemId,
                                        @RequestParam(name = "amount") Integer amount,
                                        @RequestParam(name = "promoId", required = false) Integer promoId,
                                        @RequestParam(name = "promoToken") String promoToken) throws BusinessException {
        //限制流量
        if(orderCreateRateLimiter.acquire()<=0){
            throw new BusinessException(EmBusinessError.RATELIMT);
        }

        //获取前端传来的token
        String token = httpServletRequest.getParameterMap().get("token")[0];
        if(StringUtils.isEmpty(token)){
            throw new BusinessException(PARAMETER_VALIDATION_ERROR, "用户还未登录，无法下单");
        }
        UserModel userModel = (UserModel) redisTemplate.opsForValue().get(token);
        if(userModel==null) {
            throw new BusinessException(PARAMETER_VALIDATION_ERROR, "用户还未登录，无法下单");
        }
      //校验秒杀令牌是否正确
         if(promoId!=null){
             String inRedisPromoToken = (String)redisTemplate.opsForValue().get("promo_token_"+promoId+"_userId_"+userModel.getId()+"_itemId_"+itemId);
             if(inRedisPromoToken==null){
                 throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR,"秒杀令牌校验失败");
             }
             if(StringUtils.equals(inRedisPromoToken,promoToken)){
                 throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR,"秒杀令牌校验失败");
             }
         }

        //同步调用线程池的submit方法
        //拥塞窗口为20的等待队列，用来队列化泄洪
         Future<Object> future =  executorService.submit(new Callable<Object>() {
                      @Override
                      public Object call() throws Exception {

                          //加入库存流水的init状态
                          String stockLogId=  itemService.initStockLog(itemId,amount);


                          //再去完成对应的下单事物型消息机制
                          if (!mqProducer.TransactionAsyncReduceStock(userModel.getId(), itemId, promoId, amount, stockLogId)) {
                              throw new BusinessException(EmBusinessError.UNKNOWN_ERROR,"下单失败");
                          }
                          return null;
                      }
                  });

        try {
            future.get();
        } catch (InterruptedException e) {
            throw new BusinessException(EmBusinessError.UNKNOWN_ERROR);
        } catch (ExecutionException e) {
            throw new BusinessException(EmBusinessError.UNKNOWN_ERROR);
        }


        return CommonReturnType.create(null);
    }

}
