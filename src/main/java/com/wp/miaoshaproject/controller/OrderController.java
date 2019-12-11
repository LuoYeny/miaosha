package com.wp.miaoshaproject.controller;

import com.wp.miaoshaproject.error.BusinessException;
import com.wp.miaoshaproject.error.EmBusinessError;
import com.wp.miaoshaproject.mq.MqProducer;
import com.wp.miaoshaproject.response.CommonReturnType;
import com.wp.miaoshaproject.service.ItemService;
import com.wp.miaoshaproject.service.OrderService;
import com.wp.miaoshaproject.service.model.OrderModel;
import com.wp.miaoshaproject.service.model.UserModel;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;

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

    @RequestMapping(value = "/createorder", method = {RequestMethod.POST}, consumes = {CONTENT_TYPE_FORMED})
    @ResponseBody
    public CommonReturnType createOrder(@RequestParam(name = "itemId") Integer itemId,
                                        @RequestParam(name = "amount") Integer amount,
                                        @RequestParam(name = "promoId", required = false) Integer promoId) throws BusinessException {

     //   System.out.println("order:"+httpServletRequest.getCookies());
      //  Boolean isLogin = (Boolean) httpServletRequest.getSession().getAttribute("IS_LOGIN");

        //获取前端传来的token
        String token = httpServletRequest.getParameterMap().get("token")[0];
        if(StringUtils.isEmpty(token)){
            throw new BusinessException(PARAMETER_VALIDATION_ERROR, "用户还未登录，无法下单");
        }
        UserModel userModel = (UserModel) redisTemplate.opsForValue().get(token);
        if(userModel==null) {
            throw new BusinessException(PARAMETER_VALIDATION_ERROR, "用户还未登录，无法下单");
        }

        //判断库存是否已售罄，若对应的库存售罄key存在，则直接返回下单失败
        if(redisTemplate.hasKey("promo_item_stock_invalid_"+itemId)){

            throw new BusinessException(EmBusinessError.STOCK_NOT_ENOUGH);
        }
        //获取用户信息
       // UserModel userModel = (UserModel) httpServletRequest.getSession().getAttribute("LOGIN_USER");
       //加入库存流水的init状态
      String stockLogId=  itemService.initStockLog(itemId,amount);


        //再去完成对应的下单事物型消息机制
        if (!mqProducer.TransactionAsyncReduceStock(userModel.getId(), itemId, promoId, amount, stockLogId)) {
             throw new BusinessException(EmBusinessError.UNKNOWN_ERROR,"下单失败");
        }
        return CommonReturnType.create(null);
    }

}
