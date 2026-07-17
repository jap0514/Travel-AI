package com.travel.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.travel.entity.HotelRoom;
import com.travel.service.HotelRoomService;
import com.travel.mapper.HotelRoomMapper;
import org.springframework.stereotype.Service;

/**
* @author 13922
* @description 针对表【hotel_room(物理房间表)】的数据库操作Service实现
* @createDate 2026-07-14 16:41:14
*/
@Service
public class HotelRoomServiceImpl extends ServiceImpl<HotelRoomMapper, HotelRoom>
    implements HotelRoomService{

}




