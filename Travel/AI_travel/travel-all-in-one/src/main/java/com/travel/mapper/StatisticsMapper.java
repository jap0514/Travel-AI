package com.travel.mapper;


import com.travel.vo.StatisticsDestinationVO;
import com.travel.vo.StatisticsUserDestinationVO;

import java.time.LocalDateTime;
import java.util.List;

public interface StatisticsMapper  {
    /**
     * 通过用户ID来查询用户规划旅游的目的地
     * @param pathUserId
     * @return
     */
    List<StatisticsUserDestinationVO> getUserDestinations(Long pathUserId);

    /**
     * 统计近多少天平台总的目的地
     * @param days
     * @return
     */
    List<StatisticsDestinationVO> getTotalDestinations(LocalDateTime days);
}
