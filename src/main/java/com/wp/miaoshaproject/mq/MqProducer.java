package com.wp.miaoshaproject.mq;


import com.alibaba.fastjson.JSON;
import com.wp.miaoshaproject.dao.StockLogDOMapper;
import com.wp.miaoshaproject.dataobject.StockLogDO;
import com.wp.miaoshaproject.error.BusinessException;
import com.wp.miaoshaproject.service.OrderService;
import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.*;

import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.remoting.exception.RemotingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;


/**
 * @author 罗叶妮
 * @version 1.0
 * @date 2019/12/6 17:10
 */
@Component
public class MqProducer {
    private DefaultMQProducer producer;
    @Value("${mq.nameserver.addr}")
    private String nameAddr;
    @Value("${mq.topicname}")
    private String topicName;
    private TransactionMQProducer transactionMQProducer;

    @Autowired
    private OrderService orderService;
    @Autowired
    private StockLogDOMapper stockLogDOMapper;
    @PostConstruct
    public void init() throws MQClientException {
        //mq producer 的初始化
        producer = new DefaultMQProducer("producer_group");
        producer.setNamesrvAddr(nameAddr);
        producer.start();

        transactionMQProducer = new TransactionMQProducer("transaction_producer_group");
        transactionMQProducer.setNamesrvAddr(nameAddr);
        transactionMQProducer.start();
        transactionMQProducer.setTransactionListener(new TransactionListener() {
            @Override
            public LocalTransactionState executeLocalTransaction(Message message, Object o) {

               // COMMIT_MESSAGE, 把prepare状态转换为commit
               //  ROLLBACK_MESSAGE, 消息回滚
               //  UNKNOW; 未知状态
                  Integer itemId = (Integer)((Map)o).get("itemId");
                  Integer userId = (Integer)((Map)o).get("userId");
                  Integer promoId = (Integer)((Map)o).get("promoId");
                  Integer amount = (Integer)((Map)o).get("amount");
                  String stockLogId = (String) ((Map)o).get("stockLogId");

                //真正要做的事，创建订单
                try {
                    orderService.createOrder(userId,itemId,promoId,amount,stockLogId);
                } catch (BusinessException e) {
                    e.printStackTrace();
                    StockLogDO stockLogDO =stockLogDOMapper.selectByPrimaryKey(stockLogId);
                    stockLogDO.setStatus(3);
                    stockLogDOMapper.updateByPrimaryKeySelective(stockLogDO);
                    return LocalTransactionState.ROLLBACK_MESSAGE;
                }
                return LocalTransactionState.COMMIT_MESSAGE;
            }

            /**
             * 当executeLocalTransaction由于各种原因 长时间没有返回状态
             * 系统会调用checkLocalTransaction
             * @param messageExt
             * @return
             */
            @Override
            public LocalTransactionState checkLocalTransaction(MessageExt messageExt) {
                String jsonSting = new String(messageExt.getBody());
                Map<String,Object> map = JSON.parseObject(jsonSting,Map.class);
                Integer itemId =(Integer)map.get("itemId");
                Integer amount = (Integer)map.get("amount");
                String stockLogId = (String) map.get("stockLogId");

                StockLogDO stockLogDO =stockLogDOMapper.selectByPrimaryKey(stockLogId);
                if(stockLogDO==null){
                    return LocalTransactionState.UNKNOW;
                }
                if(stockLogDO.getStatus().intValue()==2){
                    return LocalTransactionState.COMMIT_MESSAGE;
                }else if(stockLogDO.getStatus().intValue()==1){
                    return LocalTransactionState.UNKNOW;
                }
                return LocalTransactionState.ROLLBACK_MESSAGE;
            }
        });
    }
    public boolean TransactionAsyncReduceStock(Integer userId,Integer itemId,Integer promoId ,Integer amount,String stockLogId){
        Map<String ,Object> bodyMap = new HashMap<>();
        bodyMap.put("itemId",itemId);
        bodyMap.put("amount",amount);
        bodyMap.put("stockLogId",stockLogId);
        //用于传递参数
        Map<String ,Object> argsMap = new HashMap<>();
        argsMap.put("itemId",itemId);
        argsMap.put("amount",amount);
        argsMap.put("userId",userId);
        argsMap.put("promoId",promoId);
        argsMap.put("stockLogId",stockLogId);
        Message message = new Message(topicName,"increase", JSON.toJSON(bodyMap).toString().getBytes(Charset.forName("UTF-8")));
        TransactionSendResult sendResult=null;
        try {
            //投递给broker 但是 是prepare状态,此时消费端无法消费 然后去执行executeLocalTransaction
             sendResult= transactionMQProducer.sendMessageInTransaction(message,argsMap);
        } catch (MQClientException e) {
            e.printStackTrace();
            return false;
        }

        if(sendResult.getLocalTransactionState()==LocalTransactionState.ROLLBACK_MESSAGE){
            return false;
        }else if(sendResult.getLocalTransactionState()==LocalTransactionState.COMMIT_MESSAGE){
            return true;
        }else {
            return false;
        }


    }


    //同步库存扣减消息

    public boolean asyncReduceStock(Integer itemId ,Integer amount){
        Map<String ,Object> bodyMap = new HashMap<>();
        bodyMap.put("itemId",itemId);
        bodyMap.put("amount",amount);
        Message message = new Message(topicName,"increase", JSON.toJSON(bodyMap).toString().getBytes(Charset.forName("UTF-8")));
        try {
            producer.send(message);
        } catch (MQClientException e) {
            e.printStackTrace();
            return false;
        } catch (RemotingException e) {
            e.printStackTrace();
            return false;
        } catch (MQBrokerException e) {
            e.printStackTrace();
            return false;
        } catch (InterruptedException e) {
            e.printStackTrace();
            return false;
        }

        return true;

    }
}
