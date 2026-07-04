# -*- coding: utf-8 -*-
"""
地图路线 API - 提供景点路线规划接口

提供两个主要功能：
1. 地址解析（geocode）：将文字地址转换为经纬度坐标
2. 路线规划（map）：获取多个地点之间的驾车路线

使用高德地图API实现
"""
from fastapi import APIRouter, HTTPException
from pydantic import BaseModel, Field
from typing import Optional
from app.config.logger import logger
from app.utils.gaode_map import get_gaode_map_tools

# 创建路由，前缀为 /api/route
router = APIRouter(prefix="/api/route")


# ============================================================
# 请求/响应数据模型
# ============================================================

class MapRouteRequest(BaseModel):
    """地图路线请求 - POST /api/route/map"""
    start_address: str = Field(..., description="起始地址，如'广州市天河区正佳广场'")
    destinations: list[str] = Field(..., description="目的地列表，如['广州塔','陈家祠','白云山']")
    travel_mode: str = Field(default="driving", description="出行方式: driving(驾车)/walking(步行)/transit(公交)")


class Waypoint(BaseModel):
    """路线点 - 地图上显示的标记点"""
    name: str          # 地点名称
    lat: float        # 纬度
    lng: float        # 经度


class RouteSegment(BaseModel):
    """路线分段 - 记录每段路线的详细信息"""
    from_name: str = Field(alias="from")  # 起点名称
    to: str                                   # 终点名称
    distance: int  # 距离（米）
    duration: int  # 时间（秒）
    steps: list = []  # 详细步骤


class MapRouteResponse(BaseModel):
    """地图路线响应 - POST /api/route/map 返回"""
    success: bool                    # 是否成功
    start_address: str = ""         # 起始地址
    destinations: list[str] = []     # 目的地列表
    waypoints: list[Waypoint] = []  # 所有路线点（用于地图标记）
    segments: list = []             # 路线分段
    total_distance: int = 0         # 总距离（米）
    total_duration: int = 0         # 总时间（秒）
    polyline_points: list = []       # 路线折线坐标（用于绑线）
    error: Optional[str] = None     # 错误信息


# ============================================================
# 接口1：路线规划
# POST /api/route/map
# ============================================================

@router.post("/map", response_model=MapRouteResponse)
async def get_map_route(request: MapRouteRequest):
    """
    获取地图路线

    流程：
    1. 接收起点地址和目的地列表
    2. 调用高德地图工具获取完整路线
    3. 返回路线坐标点、距离、时间等信息

    前端用途：
    - waypoints：地图上显示标记点
    - polyline_points：地图上绑线绘制路线
    """
    logger.info(f"收到路线规划请求: 起点={request.start_address}, 目的地={request.destinations}")

    try:
        # 获取高德地图工具实例
        gaode = get_gaode_map_tools()

        # 调用高德地图获取完整路线
        route_result = gaode.get_full_route_with_waypoints(
            start_address=request.start_address,
            destinations=request.destinations,
            travel_mode=request.travel_mode
        )

        # 检查是否成功
        if not route_result.get("success"):
            return MapRouteResponse(
                success=False,
                error=route_result.get("error", "路线规划失败")
            )

        # 返回完整的路线信息
        return MapRouteResponse(
            success=True,
            start_address=route_result.get("start_address", ""),
            destinations=route_result.get("destinations", []),
            waypoints=route_result.get("waypoints", []),
            segments=route_result.get("segments", []),
            total_distance=route_result.get("total_distance", 0),
            total_duration=route_result.get("total_duration", 0),
            polyline_points=route_result.get("polyline_points", [])
        )

    except Exception as e:
        logger.error(f"路线规划异常: {e}")
        return MapRouteResponse(
            success=False,
            error=f"路线规划异常: {str(e)}"
        )


# ============================================================
# 接口2：地址解析
# GET /api/route/geocode?address=xxx
# ============================================================

@router.get("/geocode")
async def geocode_address(address: str):
    """
    地址解析 - 将文字地址转换为经纬度坐标

    使用高德地理编码API: https://restapi.amap.com/v3/geocode/geo

    请求示例：
        GET /api/route/geocode?address=广州市天河区广州塔

    返回示例：
        {
            "success": true,
            "lng": 113.327597,
            "lat": 23.130800,
            "province": "广东省",
            "city": "广州市",
            "district": "海珠区",
            "formatted_address": "广东省广州市海珠区..."
        }
    """
    try:
        gaode = get_gaode_map_tools()
        result = gaode.address_to_coord(address)

        if result:
            return {"success": True, **result}
        return {"success": False, "error": f"无法解析地址: {address}"}
    except Exception as e:
        return {"success": False, "error": str(e)}


# ============================================================
# 接口3：测试连接
# GET /api/route/test
# ============================================================

@router.get("/test")
async def test_gaode():
    """
    测试高德地图API连接

    使用"广州塔"作为示例地址进行测试

    返回示例：
        {
            "success": true,
            "message": "高德地图API连接正常",
            "sample": {...}  # 地址解析结果
        }
    """
    try:
        gaode = get_gaode_map_tools()
        result = gaode.address_to_coord("广州市天河区广州塔")

        if result:
            return {
                "success": True,
                "message": "高德地图API连接正常",
                "sample": result
            }
        return {"success": False, "error": "地址解析返回空"}
    except Exception as e:
        return {"success": False, "error": str(e)}
