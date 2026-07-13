package com.travel.service;

import com.travel.vo.StatisticsDestinationVO;
import com.travel.vo.StatisticsUserDestinationVO;

import java.util.List;

public interface StatisticsService {
    /**
     * 统计用户旅游规划过的目的地
     * @param pathUserId
     * @return
     */
    List<StatisticsUserDestinationVO> getUserDestinations(Long pathUserId);

    /**
     * 统计近多少天平台总的目的地
     * @param days
     * @return
     */
    List<StatisticsDestinationVO> getTotalDestinations(Integer days);
}
