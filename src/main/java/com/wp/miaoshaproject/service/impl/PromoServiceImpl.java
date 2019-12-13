package com.wp.miaoshaproject.service.impl;

import com.wp.miaoshaproject.dao.PromoDOMapper;
import com.wp.miaoshaproject.dataobject.PromoDO;
import com.wp.miaoshaproject.error.BusinessException;
import com.wp.miaoshaproject.error.EmBusinessError;
import com.wp.miaoshaproject.service.ItemService;
import com.wp.miaoshaproject.service.PromoService;
import com.wp.miaoshaproject.service.UserService;
import com.wp.miaoshaproject.service.model.ItemModel;
import com.wp.miaoshaproject.service.model.PromoModel;

import com.wp.miaoshaproject.service.model.UserModel;
import org.joda.time.DateTime;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.wp.miaoshaproject.error.EmBusinessError.PARAMETER_VALIDATION_ERROR;

/**
 * @author WangPan wangpanhust@qq.com
 * @date 2019/6/11 14:45
 * @description PromoService的实现类
 **/
@Service
public class PromoServiceImpl implements PromoService {

    @Autowired(required = false)
    private PromoDOMapper promoDOMapper;

    @Autowired
    private ItemService itemService;

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private UserService userService;

    @Override
    public PromoModel getPromoByItemId(Integer itemId) {
        PromoDO promoDO = promoDOMapper.selectByItemId(itemId);
        PromoModel promoModel = this.convertFromDataObject(promoDO);
        if (promoModel == null) {
            return null;
        }

        //判断秒杀活动状态
        if (promoModel.getStartDate().isAfterNow()) {
            promoModel.setStatus(1);
        }else if (promoModel.getEndDate().isBeforeNow()) {
            promoModel.setStatus(3);
        }else {
            promoModel.setStatus(2);
        }

        return promoModel;
    }

    private PromoModel convertFromDataObject(PromoDO promoDO) {
        if (promoDO == null) {
            return null;
        }
        PromoModel promoModel = new PromoModel();
        BeanUtils.copyProperties(promoDO, promoModel);
        promoModel.setPormoItemPrice(new BigDecimal(promoDO.getPromoItemPrice()));
        promoModel.setStartDate(new DateTime(promoDO.getStartDate()));
        promoModel.setEndDate(new DateTime(promoDO.getEndDate()));

        return promoModel;
    }

    @Override
    public void publishPromo(Integer promoId) {
        //通过活动id获取活动
        PromoDO promoDO = promoDOMapper.selectByPrimaryKey(promoId);
        if(promoDO.getItemId()==null||promoDO.getItemId().intValue()==0){
            return;
        }

        ItemModel itemModel =itemService.getItemById(promoDO.getItemId());
        //将库存同步到redis内

        redisTemplate.opsForValue().set("promo_item_stock_"+itemModel.getId(),itemModel.getStock());

        //将大闸的限制数字设置到redis内
        redisTemplate.opsForValue().set("promo_door_count_"+promoId,itemModel.getStock().intValue()*5);

    }

    @Override
    public String generateSecondKillToken(Integer promoId,Integer itemId,Integer userId) {
        //判断库存是否已售罄，若对应的库存售罄key存在，则直接返回下单失败
        if(redisTemplate.hasKey("promo_item_stock_invalid_"+itemId)){

            return null;
        }
        //判断item消息是否存在
        ItemModel itemModel= itemService.getItemByIdInCache(itemId);
        if (itemModel == null) {
            return null;
        }

        // 判断用户是否存在;
        UserModel userModel= userService.getUserByIdInCache(userId);
        if (userModel == null) {
            return null;
        }

        //获取秒杀大闸的count数量
        long result = redisTemplate.opsForValue().decrement("promo_door_count_"+promoId,1);
        if(result<0){
            System.out.println("关大闸");
            return null;
        }



        PromoDO promoDO = promoDOMapper.selectByPrimaryKey(promoId);
        PromoModel promoModel = this.convertFromDataObject(promoDO);
        if (promoModel == null) {
            return null;
        }

        //判断秒杀活动状态
        if (promoModel.getStartDate().isAfterNow()) {
            promoModel.setStatus(1);
        }else if (promoModel.getEndDate().isBeforeNow()) {
            promoModel.setStatus(3);
        }else {
            promoModel.setStatus(2);
        }
        //活动正开始
        if(promoModel.getStatus().intValue()!=2){
            return null;
        }
       String token = UUID.randomUUID().toString().replace("-","");
        redisTemplate.opsForValue().set("promo_token_"+promoId+"_userId_"+userId+"_itemId_"+itemId,token);
        redisTemplate.expire("promo_token_"+promoId+"_userId_"+userId+"_itemId_",5, TimeUnit.MINUTES);

        return token;
    }
}
