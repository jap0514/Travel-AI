package com.travel.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.travel.entity.TravelParsePlan;
import com.travel.service.TravelParsePlanService;
import com.travel.mapper.TravelParsePlanMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
* @author 13922
* @description 针对表【travel_parse_plan(旅行行程表)】的数据库操作Service实现
* @createDate 2026-07-13 10:25:03
*/
@Service
public class TravelParsePlanServiceImpl extends ServiceImpl<TravelParsePlanMapper, TravelParsePlan>
    implements TravelParsePlanService{

    @Override
    public List<TravelParsePlan> getUserPlans(Long userId) {
        if (userId == null) return Collections.emptyList();
        LambdaQueryWrapper<TravelParsePlan> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TravelParsePlan::getUserId, userId);
        wrapper.orderByDesc(TravelParsePlan::getCreateTime);
        List<TravelParsePlan> travelParsePlans = new ArrayList<>();
        travelParsePlans=this.list(wrapper);
        return travelParsePlans;
    }
}




