package com.travel.controller;

import com.travel.common.Result;
import com.travel.service.StatisticsService;
import com.travel.vo.StatisticsDestinationVO;
import com.travel.vo.StatisticsUserDestinationVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

/**
 * 做数据的统计，比如用户常去的目的地
 */

@RestController
@RequestMapping("/statistics")
public class StatisticsController {

    @Autowired
    private StatisticsService statisticsService;

    /**
     * 统计某个用户去过的目的地
     * @param PathUserId  查询时从访问路径上带的用户Id
     * @param userId  当前登录的用户Id
     * @return
     */
    @GetMapping("/user/{userId}/destinations")
    public Result<List<StatisticsUserDestinationVO>> getUserDestinations(@PathVariable("userId") Long PathUserId,
                                                                         @RequestAttribute Long userId){
        List<StatisticsUserDestinationVO> destinationsArrayList = new ArrayList<>();
        destinationsArrayList=statisticsService.getUserDestinations(PathUserId);
        return Result.success(destinationsArrayList);
    }



    /**
     * 统计所有用户中的热门目的地
     */
    @GetMapping("/destinations")
    public Result<List<StatisticsDestinationVO>> getTotalDestinations(@RequestParam("days") Integer days){
        List<StatisticsDestinationVO> destinationsArrayList = new ArrayList<>();
        destinationsArrayList=statisticsService.getTotalDestinations(days);
        return Result.success(destinationsArrayList);
    }
}
