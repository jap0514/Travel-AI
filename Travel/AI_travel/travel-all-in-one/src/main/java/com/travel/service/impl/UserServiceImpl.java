package com.travel.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.travel.entity.User;
import com.travel.service.UserService;
import com.travel.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
* @author 13922
* @description 针对表【user(用户表)】的数据库操作Service实现
* @createDate 2026-04-25 16:27:23
*/
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User>
    implements UserService{

    @Autowired
    private UserMapper userMapper;

    @Override
    public IPage<User> getUserPage(Integer pageNum, Integer pageSize) {
        //1、创建分页对象
        Page<User> page=new Page<>(pageNum,pageSize);

        //2、条件构造器
        LambdaQueryWrapper<User> wrapper= Wrappers.lambdaQuery();
        wrapper.orderByDesc(User::getCreate_time);

        //3、分页查询
        return userMapper.selectPage(page,wrapper);
    }
}




