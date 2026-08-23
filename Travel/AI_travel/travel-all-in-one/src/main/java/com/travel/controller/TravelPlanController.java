package com.travel.controller;

import com.travel.common.Result;
import com.travel.entity.TravelParsePlan;
import com.travel.service.TravelParsePlanService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 行程规划 Controller
 * <p>
 * 路径前缀 /travel-plan，不在 /hotel/** 排除范围内，AuthInterceptor 正常拦截。
 */
@RestController
@RequestMapping("/travel-plan")
public class TravelPlanController {

    @Autowired
    private TravelParsePlanService travelParsePlanService;

    /**
     * 查询某用户的所有行程（按创建时间倒序）
     * @param userId 用户ID（从 token 自动获取）
     * @return 行程列表
     */
    @GetMapping("/user/{userId}")
    public Result<List<TravelParsePlan>> getUserPlans(@PathVariable("userId") Long userId,
                                                       @RequestAttribute("userId") Long currentUserId) {
        // 防止越权：只能查自己的行程
        if (!userId.equals(currentUserId)) {
            return Result.error(com.travel.common.ResultCode.FORBIDDEN, "无权查看其他用户的行程");
        }
        List<TravelParsePlan> plans = travelParsePlanService.getUserPlans(userId);
        return Result.success(plans);
    }
}