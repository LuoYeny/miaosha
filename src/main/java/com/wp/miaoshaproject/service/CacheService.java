package com.wp.miaoshaproject.service;

/**
 * @author 罗叶妮
 * @version 1.0
 * @date 2019/12/3 16:15
 */
public interface CacheService {
    //存方法
    void setCommonCache(String key , Object value);
    //取方法
    Object getFormCommonCache(String key);
}
