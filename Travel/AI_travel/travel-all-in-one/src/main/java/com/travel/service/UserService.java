package com.travel.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.travel.entity.User;
import com.baomidou.mybatisplus.extension.service.IService;

/**
* @author 13922
* @description 针对表【user(用户表)】的数据库操作Service
* @createDate 2026-04-25 16:27:23
*/
public interface UserService extends IService<User> {

    /**
     * 分页查询用户信息
     * @param pageNum
     * @param pageSize
     * @return 用户信息列表
     */
    IPage<User> getUserPage(Integer pageNum, Integer pageSize);
}
