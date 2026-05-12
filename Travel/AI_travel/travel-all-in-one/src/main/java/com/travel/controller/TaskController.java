package com.travel.controller;

import com.travel.common.Result;
import com.travel.dto.TaskDTO;
import com.travel.entity.TravelTask;
import com.travel.service.TravelTaskService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/task")
public class TaskController {
    @Autowired
    private TravelTaskService travelTaskService;

    /**
     * 新增任务
     */
    @PostMapping("/addTask")
    public Result addTask(@Valid @RequestBody TaskDTO taskDTO,
                          @RequestAttribute Long userId){
        System.out.println("到达addTask");
//        travelTaskService.saveAndSendMQ(taskDTO,userId);

        return Result.success();
    }
}
