package com.wp.miaoshaproject.service;

import com.wp.miaoshaproject.error.BusinessException;
import com.wp.miaoshaproject.service.model.UserModel;

public interface UserService {

    UserModel getUserById(Integer id);

    void register(UserModel userModel) throws BusinessException;

    UserModel validateLogin(String telphone, String encrptPassword) throws BusinessException;

    //通过缓存获取用户对象
    UserModel getUserByIdInCache(Integer id);

}
