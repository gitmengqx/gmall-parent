package com.atguigu.gmall.user.service;

import com.atguigu.gmall.model.user.UserAddress;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface UserAddressService extends IService<UserAddress> {
    List<UserAddress> findUserAddressListByUserId(String userId);
}
