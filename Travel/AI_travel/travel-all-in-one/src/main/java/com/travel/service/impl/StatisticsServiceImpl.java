package com.travel.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.travel.entity.TravelParsePlan;
import com.travel.mapper.StatisticsMapper;
import com.travel.service.StatisticsService;
import com.travel.vo.StatisticsDestinationVO;
import com.travel.vo.StatisticsUserDestinationVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class StatisticsServiceImpl implements StatisticsService {

    @Autowired
    private StatisticsMapper statisticsMapper;

    /**
     * 统计用户旅游规划过的目的地
     * @param pathUserId
     * @return
     */
    @Override
    public List<StatisticsUserDestinationVO> getUserDestinations(Long pathUserId) {
        //根据用户Id到travel_parse_plan表去查询destination字段，如果destination相同，就统计有多少次
        List<StatisticsUserDestinationVO> UserDestinationList=new ArrayList<>();
        UserDestinationList=statisticsMapper.getUserDestinations(pathUserId);

        System.out.println("返回结果：---------");
        System.out.println(UserDestinationList);

        return UserDestinationList;
    }

    /**
     * 统计近多少天平台总的目的地
     * @param days
     * @return
     */
    @Override
    public List<StatisticsDestinationVO> getTotalDestinations(Integer days) {
        //计算需要统计的日期
        LocalDateTime DaysAgo=LocalDateTime.now().minusDays(days);

        return statisticsMapper.getTotalDestinations(DaysAgo);
    }


}
