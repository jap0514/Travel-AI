# -*- coding: utf-8 -*-
"""
高德地图工具类 - 用于地址解析、路线规划等
"""
import requests
import math
import json
from typing import Optional
from app.config.logger import logger

# 高德 API 配置
GAODE_API_KEY = "f8ae6ab929cf3829e036c87c620ef804"
GEOCODE_URL = "https://restapi.amap.com/v3/geocode/geo"
DIRECTION_URL = "https://restapi.amap.com/v5/direction"


class GaodeMapTools:
    """高德地图工具类"""

    def __init__(self, api_key: str = None):
        self.api_key = api_key or GAODE_API_KEY

    def address_to_coord(self, address: str) -> Optional[dict]:
        """
        将地址转换为经纬度坐标

        Args:
            address: 地址字符串，如 "广州市天河区广州塔"

        Returns:
            {"lat": 23.123, "lng": 113.456} 或 None
        """
        try:
            params = {
                "key": self.api_key,
                "address": address,
                "output": "json"
            }
            response = requests.get(GEOCODE_URL, params=params, timeout=10)
            data = response.json()

            if data.get("status") == "1" and data.get("geocodes"):
                geocode = data["geocodes"][0]
                location = geocode.get("location", "")
                if location:
                    lng, lat = location.split(",")
                    return {
                        "lat": float(lat),
                        "lng": float(lng),
                        "province": geocode.get("province", ""),
                        "city": geocode.get("city", ""),
                        "district": geocode.get("district", ""),
                        "formatted_address": geocode.get("formatted_address", "")
                    }
            logger.warning(f"地址解析失败: {address}, response: {data}")
            return None
        except Exception as e:
            logger.error(f"地址解析异常: {address}, error: {e}")
            return None

    def batch_address_to_coord(self, addresses: list) -> dict:
        """
        批量将地址转换为坐标

        Args:
            addresses: 地址列表

        Returns:
            {地址: {"lat": , "lng": }, ...}
        """
        results = {}
        for addr in addresses:
            if addr:
                coord = self.address_to_coord(addr)
                if coord:
                    results[addr] = coord
                else:
                    # 尝试添加城市前缀后重试
                    coord = self.address_to_coord(f"广州市{addr}")
                    if coord:
                        results[addr] = coord
        return results

    def driving_route(self, start_coord: str, end_coord: str,
                      waypoints: list = None) -> Optional[dict]:
        """
        驾车路线规划

        Args:
            start_coord: 起点坐标 "lng,lat"
            end_coord: 终点坐标 "lng,lat"
            waypoints: 途经点坐标列表 ["lng,lat", ...]

        Returns:
            路线详情 dict 或 None
        """
        try:
            params = {
                "key": self.api_key,
                "origin": start_coord,
                "destination": end_coord,
                "strategy": "0",  # 0:推荐模式
                "show_fields": "cost,polyline",
            }

            if waypoints:
                params["waypoints"] = ";".join(waypoints)

            url = f"{DIRECTION_URL}/driving"
            response = requests.get(url, params=params, timeout=15)
            data = response.json()

            if data.get("status") == "1":
                route = data.get("route", {})
                paths = route.get("paths", [])
                if paths:
                    first_path = paths[0]
                    return {
                        "distance": int(first_path.get("distance", 0)),
                        "duration": int(first_path.get("duration", 0)),
                        "steps": self._parse_driving_steps(first_path.get("steps", []))
                    }
            logger.warning(f"驾车路线规划失败: {data}")
            return None
        except Exception as e:
            logger.error(f"驾车路线规划异常: {e}")
            return None

    def walking_route(self, start_coord: str, end_coord: str) -> Optional[dict]:
        """
        步行路线规划

        Args:
            start_coord: 起点坐标 "lng,lat"
            end_coord: 终点坐标 "lng,lat"

        Returns:
            路线详情 dict 或 None
        """
        try:
            params = {
                "key": self.api_key,
                "origin": start_coord,
                "destination": end_coord,
            }

            url = f"{DIRECTION_URL}/walking"
            response = requests.get(url, params=params, timeout=10)
            data = response.json()

            if data.get("status") == "1":
                route = data.get("route", {})
                paths = route.get("paths", [])
                if paths:
                    first_path = paths[0]
                    return {
                        "distance": int(first_path.get("distance", 0)),
                        "duration": int(first_path.get("duration", 0)),
                        "steps": self._parse_walking_steps(first_path.get("steps", []))
                    }
            return None
        except Exception as e:
            logger.error(f"步行路线规划异常: {e}")
            return None

    def transit_route(self, start_city: str, end_city: str,
                     start_coord: str = None, end_coord: str = None,
                     city_adcode: str = "440100") -> Optional[dict]:
        """
        公交路线规划

        Args:
            start_city: 起点城市
            end_city: 终点城市
            city_adcode: 城市编码（默认广州440100）

        Returns:
            路线详情 dict 或 None
        """
        try:
            params = {
                "key": self.api_key,
                "origin": start_city,
                "destination": end_city,
                "city": city_adcode,
                "strategy": "0",
            }

            url = f"{DIRECTION_URL}/transit"
            response = requests.get(url, params=params, timeout=10)
            data = response.json()

            if data.get("status") == "1":
                route = data.get("route", {})
                return route
            return None
        except Exception as e:
            logger.error(f"公交路线规划异常: {e}")
            return None

    def _parse_driving_steps(self, steps: list) -> list:
        """解析驾车路线步骤"""
        result = []
        for step in steps:
            step_info = {
                "instruction": step.get("instruction", ""),
                "road_name": step.get("road_name", ""),
                "distance": int(step.get("distance", 0)),
                "duration": int(step.get("duration", 0)),
            }
            # 尝试获取该步骤的坐标点
            if step.get("polyline"):
                coords = step["polyline"].split(";")
                if coords:
                    first = coords[0].split(",")
                    last = coords[-1].split(",")
                    if len(first) >= 2 and len(last) >= 2:
                        try:
                            step_info["start_point"] = {
                                "lng": float(first[0]),
                                "lat": float(first[1])
                            }
                            step_info["end_point"] = {
                                "lng": float(last[0]),
                                "lat": float(last[1])
                            }
                        except ValueError:
                            pass
            result.append(step_info)
        return result

    def _parse_walking_steps(self, steps: list) -> list:
        """解析步行路线步骤"""
        result = []
        for step in steps:
            step_info = {
                "instruction": step.get("instruction", ""),
                "distance": int(step.get("distance", 0)),
                "duration": int(step.get("duration", 0)),
            }
            if step.get("polyline"):
                coords = step["polyline"].split(";")
                if coords:
                    first = coords[0].split(",")
                    last = coords[-1].split(",")
                    if len(first) >= 2 and len(last) >= 2:
                        try:
                            step_info["start_point"] = {
                                "lng": float(first[0]),
                                "lat": float(first[1])
                            }
                            step_info["end_point"] = {
                                "lng": float(last[0]),
                                "lat": float(last[1])
                            }
                        except ValueError:
                            pass
            result.append(step_info)
        return result

    def get_full_route_with_waypoints(self, start_address: str,
                                       destinations: list,
                                       travel_mode: str = "driving") -> dict:
        """
        获取包含途经点的完整路线

        Args:
            start_address: 起始地址
            destinations: 目的地列表（景点名称）
            travel_mode: "driving" | "walking" | "transit"

        Returns:
            完整路线信息，包含所有拐点坐标
        """
        result = {
            "success": False,
            "start_address": start_address,
            "destinations": destinations,
            "waypoints": [],  # 所有点的坐标，用于前端绑点
            "segments": [],  # 路线分段信息
            "total_distance": 0,
            "total_duration": 0
        }

        # 1. 解析所有地址为坐标
        logger.info(f"开始解析地址: {start_address} + {destinations}")

        start_coord = self.address_to_coord(start_address)
        if not start_coord:
            result["error"] = f"无法解析起始地址: {start_address}"
            return result

        dest_coords = []
        for dest in destinations:
            coord = self.address_to_coord(dest)
            if coord:
                dest_coords.append({"name": dest, **coord})
            else:
                # 尝试添加城市前缀
                coord = self.address_to_coord(f"{start_address.split('市')[0] if '市' in start_address else ''}市{dest}")
                if coord:
                    dest_coords.append({"name": dest, **coord})

        if not dest_coords:
            result["error"] = "无法解析任何目的地地址"
            return result

        # 2. 添加起点到 waypoints
        result["waypoints"].append({
            "name": start_address,
            "lat": start_coord["lat"],
            "lng": start_coord["lng"]
        })

        # 3. 规划各段路线
        current_point = f"{start_coord['lng']},{start_coord['lat']}"
        all_polyline_points = []

        for i, dest in enumerate(dest_coords):
            end_point = f"{dest['lng']},{dest['lat']}"

            if travel_mode == "driving":
                # 驾车路线：最多16个途经点
                route = self.driving_route(current_point, end_point)
            elif travel_mode == "walking":
                route = self.walking_route(current_point, end_point)
            else:
                route = self.driving_route(current_point, end_point)

            if route:
                result["segments"].append({
                    "from": result["waypoints"][-1]["name"],
                    "to": dest["name"],
                    "distance": route.get("distance", 0),
                    "duration": route.get("duration", 0),
                    "steps": route.get("steps", [])
                })
                result["total_distance"] += route.get("distance", 0)
                result["total_duration"] += route.get("duration", 0)

                # 收集所有坐标点用于前端绑线
                for seg in route.get("steps", []):
                    if "start_point" in seg:
                        all_polyline_points.append([seg["start_point"]["lng"], seg["start_point"]["lat"]])
                    if "end_point" in seg:
                        all_polyline_points.append([seg["end_point"]["lng"], seg["end_point"]["lat"]])

            current_point = end_point
            result["waypoints"].append({
                "name": dest["name"],
                "lat": dest["lat"],
                "lng": dest["lng"]
            })

        result["success"] = True
        result["polyline_points"] = all_polyline_points

        return result


# 全局单例
_gaode_map_tools = None

def get_gaode_map_tools() -> GaodeMapTools:
    """获取高德地图工具单例"""
    global _gaode_map_tools
    if _gaode_map_tools is None:
        _gaode_map_tools = GaodeMapTools()
    return _gaode_map_tools
