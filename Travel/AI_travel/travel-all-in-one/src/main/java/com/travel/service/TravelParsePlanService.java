package com.travel.service;

import com.travel.entity.TravelParsePlan;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
* @author 13922
* @description 针对表【travel_parse_plan(旅行行程表)】的数据库操作Service
* @createDate 2026-07-13 10:25:03
*/
public interface TravelParsePlanService extends IService<TravelParsePlan> {

    /**
     * 查询某用户的所有行程（按创建时间倒序）
     * @param userId 用户ID
     * @return 行程列表
     */
    List<TravelParsePlan> getUserPlans(Long userId);
}