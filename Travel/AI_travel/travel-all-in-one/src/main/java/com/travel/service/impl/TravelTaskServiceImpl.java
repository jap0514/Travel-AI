package com.travel.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.travel.entity.TravelTask;
import com.travel.service.TravelTaskService;
import com.travel.mapper.TravelTaskMapper;
import org.springframework.stereotype.Service;

/**
* @author 13922
* @description 针对表【travel_task(行程任务表)】的数据库操作Service实现
* @createDate 2026-05-11 13:03:05
*/
@Service
public class TravelTaskServiceImpl extends ServiceImpl<TravelTaskMapper, TravelTask>
    implements TravelTaskService{

}




