package com.travel.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.travel.entity.TravelPlan;
import com.travel.service.TravelPlanService;
import com.travel.mapper.TravelPlanMapper;
import org.springframework.stereotype.Service;

/**
* @author 13922
* @description 针对表【travel_plan(行程规划表)】的数据库操作Service实现
* @createDate 2026-05-11 13:04:14
*/
@Service
public class TravelPlanServiceImpl extends ServiceImpl<TravelPlanMapper, TravelPlan>
    implements TravelPlanService{

}




