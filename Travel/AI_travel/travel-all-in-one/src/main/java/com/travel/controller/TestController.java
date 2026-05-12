package com.travel.controller;

import com.travel.common.Result;
import com.travel.mq.TravelTaskProducer;
import com.travel.util.RedisUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/test")
public class TestController {

    @GetMapping("/hello")
    public Result<String> hello() {
        return Result.success("旅游AI系统启动成功！连接中间件正常！");
    }

    @Autowired
    private RedisUtil redisUtil;

    @Autowired
    private TravelTaskProducer travelTaskProducer;

    @GetMapping("/testRedis")
    public String testRedis() {
        // 存入字符串
        redisUtil.set("name", "张三", 60);   // 60秒过期
        // 取出字符串
        String name = (String) redisUtil.get("name");
        System.out.println("name = " + name);

        // 存入 Hash
        redisUtil.hset("user:1001", "age", 25);
        Object age = redisUtil.hget("user:1001", "age");
        System.out.println("age = " + age);

        // 自增操作
        redisUtil.set("count", 1);
        redisUtil.incr("count", 1);
        System.out.println("count = " + redisUtil.get("count"));  // 输出 2

        return "success";
    }

    @GetMapping("/testMQ")
    public String testMQSend(){
//        travelTaskProducer.sendReliableMessage("这是 Travel 项目的异步任务消息");
        return "消息发送成功";
    }


}