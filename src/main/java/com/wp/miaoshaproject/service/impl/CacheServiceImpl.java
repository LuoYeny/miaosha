package com.wp.miaoshaproject.service.impl;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import com.wp.miaoshaproject.service.CacheService;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * @author 罗叶妮
 * @version 1.0
 * @date 2019/12/3 16:17
 */
@Service
public class CacheServiceImpl implements CacheService {
    private Cache<Object, Object> commonCache = null;
    @PostConstruct
    public void init(){
        commonCache = CacheBuilder.newBuilder()
        //设置缓存容器的初始容量为10
        .initialCapacity(10)
                //设置缓冲器中最大可储存100个key，超过100个之后会按照LRU(最少使用)策略移除缓存项
                .maximumSize(100)
                //设置写入缓存后多久过期
                .expireAfterWrite(60, TimeUnit.SECONDS).build();

    }
    @Override
    public void setCommonCache(String key, Object value) {
          commonCache.put(key,value);
    }

    @Override
    public Object getFormCommonCache(String key) {
        return commonCache.getIfPresent(key);
    }
}
