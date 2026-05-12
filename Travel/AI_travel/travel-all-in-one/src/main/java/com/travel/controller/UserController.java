package com.travel.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.travel.common.Result;
import com.travel.entity.User;
import com.travel.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/user")
public class UserController {
    @Autowired
    private UserService userService;

    /**
     * 查询单个用户信息
     */
    @GetMapping("/getUserById")
    public Result<User> getUserInfo(@RequestParam Long userId){
        User userResult=userService.getById(userId);
        return Result.success(userResult);
    }

    /**
     * 分页查询用户信息
     */
    @GetMapping("/list")
    public Result<IPage<User>> getUserList(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize
    ){
        IPage<User> page=userService.getUserPage(pageNum,pageSize);
        return Result.success(page);
    }

    /**
     * 新增单个用户
     */


    /**
     * 删除单个用户
     */


    /**
     * 更新用户信息
     */
}
