# -*- coding: utf-8 -*-
"""
地图路线 API - 提供景点路线规划接口
"""
from fastapi import APIRouter, HTTPException
from pydantic import BaseModel, Field
from typing import Optional
from app.config.logger import logger
from app.utils.gaode_map import get_gaode_map_tools

router = APIRouter(prefix="/api/route")


class MapRouteRequest(BaseModel):
    """地图路线请求"""
    start_address: str = Field(..., description="起始地址")
    destinations: list[str] = Field(..., description="目的地列表（景点名称）")
    travel_mode: str = Field(default="driving", description="出行方式: driving/walking/transit")


class Waypoint(BaseModel):
    """路线点"""
    name: str
    lat: float
    lng: float


class RouteSegment(BaseModel):
    """路线分段"""
    from_name: str = Field(alias="from")
    to: str
    distance: int  # 米
    duration: int  # 秒
    steps: list = []


class MapRouteResponse(BaseModel):
    """地图路线响应"""
    success: bool
    start_address: str = ""
    destinations: list[str] = []
    waypoints: list[Waypoint] = []
    segments: list = []
    total_distance: int = 0  # 米
    total_duration: int = 0  # 秒
    polyline_points: list = []  # 绑线坐标点
    error: Optional[str] = None


@router.post("/map", response_model=MapRouteResponse)
async def get_map_route(request: MapRouteRequest):
    """
    获取地图路线

    - 输入起始地址和景点列表
    - 返回各点坐标和路线详情
    - 用于前端展示地图路线
    """
    logger.info(f"收到路线规划请求: 起点={request.start_address}, 目的地={request.destinations}")

    try:
        gaode = get_gaode_map_tools()

        # 调用高德地图获取路线
        route_result = gaode.get_full_route_with_waypoints(
            start_address=request.start_address,
            destinations=request.destinations,
            travel_mode=request.travel_mode
        )

        if not route_result.get("success"):
            return MapRouteResponse(
                success=False,
                error=route_result.get("error", "路线规划失败")
            )

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


@router.get("/geocode")
async def geocode_address(address: str):
    """
    地址解析 - 将地址转换为坐标

    - 输入: address=广州市天河区
    - 返回: {lng, lat, formatted_address}
    """
    try:
        gaode = get_gaode_map_tools()
        result = gaode.address_to_coord(address)

        if result:
            return {"success": True, **result}
        return {"success": False, "error": f"无法解析地址: {address}"}
    except Exception as e:
        return {"success": False, "error": str(e)}


@router.get("/test")
async def test_gaode():
    """测试高德地图连接"""
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
