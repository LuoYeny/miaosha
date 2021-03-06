package com.wp.miaoshaproject.service.impl;

import com.wp.miaoshaproject.dao.OrderDOMapper;
import com.wp.miaoshaproject.dao.SequenceDOMapper;
import com.wp.miaoshaproject.dao.StockLogDOMapper;
import com.wp.miaoshaproject.dataobject.OrderDO;
import com.wp.miaoshaproject.dataobject.SequenceDO;
import com.wp.miaoshaproject.dataobject.StockLogDO;
import com.wp.miaoshaproject.error.BusinessException;
import com.wp.miaoshaproject.error.EmBusinessError;
import com.wp.miaoshaproject.service.ItemService;
import com.wp.miaoshaproject.service.OrderService;
import com.wp.miaoshaproject.service.UserService;
import com.wp.miaoshaproject.service.model.ItemModel;
import com.wp.miaoshaproject.service.model.OrderModel;
import com.wp.miaoshaproject.service.model.UserModel;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationAdapter;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static com.wp.miaoshaproject.error.EmBusinessError.PARAMETER_VALIDATION_ERROR;
import static com.wp.miaoshaproject.error.EmBusinessError.STOCK_NOT_ENOUGH;

/**
 * @author WangPan wangpanhust@qq.com
 * @date 2019/6/10 14:18
 * @description OrderService的实现类
 **/
@Service
public class OrderServiceImpl implements OrderService {

    @Autowired
    private ItemService itemService;

    @Autowired
    private UserService userService;

    @Autowired(required = false)
    private OrderDOMapper orderDOMapper;

    @Autowired(required = false)
    private SequenceDOMapper sequenceDOMapper;

    @Autowired
    private StockLogDOMapper stockLogDOMapper;

    @Override
    @Transactional
    public OrderModel createOrder(Integer userId, Integer itemId, Integer promoId, Integer amount,String stockLogId) throws BusinessException {

        //1、校验下单状态，商品是否存在，用户是否合法，购买数量是否正确
      //  ItemModel itemModel = itemService.getItemById(itemId);
        ItemModel itemModel= itemService.getItemByIdInCache(itemId);

        if (itemModel == null) {
            throw new BusinessException(PARAMETER_VALIDATION_ERROR, "商品信息不存在");
        }
        //已经用秒杀令牌验证过了
//
//       // UserModel userModel = userService.getUserById(userId);
//        UserModel userModel= userService.getUserByIdInCache(userId);
//        if (userModel == null) {
//            throw new BusinessException(PARAMETER_VALIDATION_ERROR, "用户信息不存在");
//        }

        //限制购买数量不能大于99
        if (amount < 1 || amount > 99) {
            throw new BusinessException(PARAMETER_VALIDATION_ERROR, "商品购买数量不正确");
        }

//        if (promoId != null) {
//            //校验活动是否存在适用商品
//            if (promoId.intValue() != itemModel.getPromoModel().getId()) {
//                throw new BusinessException(PARAMETER_VALIDATION_ERROR, "活动信息不正确");
//                //校验秒杀活动是否正在进行
//            }else if (itemModel.getPromoModel().getStatus().intValue() == 1) {
//                throw new BusinessException(PARAMETER_VALIDATION_ERROR, "活动还未开始");
//            }else if (itemModel.getPromoModel().getStatus().intValue() == 3) {
//                throw new BusinessException(PARAMETER_VALIDATION_ERROR, "活动已经结束");
//            }
//        }

        //2、落单减库存（本例），支付减库存（其他方式）
        //减Redis
        boolean result = itemService.decreaseStcok(itemId, amount);
        if (!result) {
            throw new BusinessException(EmBusinessError.STOCK_NOT_ENOUGH);
        }

        //3、订单入库
        OrderModel orderModel = new OrderModel();
        orderModel.setUserId(userId);
        orderModel.setItemId(itemId);
        orderModel.setAmount(amount);
        if (promoId != null) {
            orderModel.setItemPrice(itemModel.getPromoModel().getPormoItemPrice());
        }else {
            orderModel.setItemPrice(itemModel.getPrice());
        }
        orderModel.setPromoId(promoId);
        orderModel.setOrderPrice(orderModel.getItemPrice().multiply(new BigDecimal(amount)));
        orderModel.setId(this.generateOrderNo());

        OrderDO orderDO = convertFromModel(orderModel);
        try {
            orderDOMapper.insertSelective(orderDO);
        } catch (Exception e) {
            e.printStackTrace();
        }

        //商品销量对应增加
        itemService.increaseSales(itemId, amount);
         //设置库存流水状态为成功
        StockLogDO stockLogDO = stockLogDOMapper.selectByPrimaryKey(stockLogId);
        if(stockLogDO==null){
            throw new BusinessException(EmBusinessError.UNKNOWN_ERROR);
        }
        stockLogDO.setStatus(2);
        stockLogDOMapper.updateByPrimaryKeySelective(stockLogDO);
        //距离最近的@Transaction更新成功后执行
//        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronizationAdapter() {
//            @Override
//            public void afterCommit() {
//                //异步更新库存
//                boolean mqResult = itemService.asyncDecreaseStcok(itemId,amount);
////                if(!mqResult){
////                    itemService.increaseSales(itemId,amount);
////                    throw  new BusinessException(EmBusinessError.MQ_SEND_FAIL);
////                }
//
//            }
//        });
        //4、返回前端
        return orderModel;
    }

    /**
     生成订单号，订单号16位，且就算更新订单的事务失败回滚，这个sequence也不应该被使用，即sequence必须是更新成功，不允许回滚，保证全局唯一性
     */

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    String generateOrderNo() {

        StringBuilder stringBuilder = new StringBuilder();

        //前8位为时间信息
        LocalDateTime now = LocalDateTime.now();
        String nowDate = now.format(DateTimeFormatter.ISO_DATE).replace("-", "");
        stringBuilder.append(nowDate);

        //中间六位为自增序列，表示当天内的订单数量
        int sequence = 0;
        SequenceDO sequenceDO = sequenceDOMapper.getSequenceByName("order_info");

        sequence = sequenceDO.getCurrentValue();
        sequenceDO.setCurrentValue(sequenceDO.getCurrentValue() + sequenceDO.getStep());
        sequenceDOMapper.updateByPrimaryKeySelective(sequenceDO);

        String sequenceStr = String.valueOf(sequence);
        //对不足6位的序列前面加0，这里未考虑超过6位的序列
        for (int i = 0; i < 6 - sequenceStr.length(); i++) {
            stringBuilder.append("0");
        }
        stringBuilder.append(sequenceStr);

        //最后两位为分库分表位，这里固定
        stringBuilder.append("00");

        return stringBuilder.toString();
    }

    private OrderDO convertFromModel(OrderModel orderModel) {
        if (orderModel == null) {
            return null;
        }
        OrderDO orderDO = new OrderDO();
        BeanUtils.copyProperties(orderModel, orderDO);

        orderDO.setItemPrice(orderModel.getItemPrice().doubleValue());
        orderDO.setOrderPrice(orderModel.getOrderPrice().doubleValue());
        return orderDO;
    }
}












