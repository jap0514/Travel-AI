# -*- coding: utf-8 -*-
"""
高德地图工具类 - 提供地址解析、路线规划等功能

功能：
1. 地址解析（address_to_coord）：将文字地址转换为经纬度坐标
2. 批量地址解析（batch_address_to_coord）：批量转换
3. 驾车路线（driving_route）：获取驾车路线
4. 步行路线（walking_route）：获取步行路线
5. 公交路线（transit_route）：获取公交路线
6. 完整路线（get_full_route_with_waypoints）：多景点完整路线

使用高德地图Web服务API：https://lbs.amap.com/
"""
import requests
import math
import json
from typing import Optional
from app.config.logger import logger

# 高德 API 配置
GAODE_API_KEY = "f8ae6ab929cf3829e036c87c620ef804"  # 高德地图API Key
GEOCODE_URL = "https://restapi.amap.com/v3/geocode/geo"  # 地理编码API（地址→坐标）
DIRECTION_URL = "https://restapi.amap.com/v5/direction"  # 路线规划API


class GaodeMapTools:
    """高德地图工具类"""

    def __init__(self, api_key: str = None):
        """初始化

        Args:
            api_key: 高德API Key，默认使用全局配置的Key
        """
        self.api_key = api_key or GAODE_API_KEY

    # ============================================================
    # 地址解析
    # ============================================================

    def address_to_coord(self, address: str) -> Optional[dict]:
        """
        将文字地址转换为经纬度坐标

        使用高德地理编码API：GET https://restapi.amap.com/v3/geocode/geo

        Args:
            address: 地址字符串，如 "广州市天河区广州塔"

        Returns:
            成功返回：
            {
                "lat": 23.130800,   # 纬度
                "lng": 113.327597,   # 经度
                "province": "广东省",
                "city": "广州市",
                "district": "海珠区",
                "formatted_address": "广东省广州市海珠区..."
            }
            失败返回 None
        """
        try:
            # 构造API请求参数
            params = {
                "key": self.api_key,
                "address": address,
                "output": "json"
            }

            # 调用高德地理编码API
            response = requests.get(GEOCODE_URL, params=params, timeout=10)
            data = response.json()

            # 检查返回状态
            # status="1" 表示成功，geocodes 数组包含解析结果
            if data.get("status") == "1" and data.get("geocodes"):
                geocode = data["geocodes"][0]  # 取第一个结果
                location = geocode.get("location", "")  # "113.327,23.130"

                if location:
                    # 拆分经纬度（格式："经度,纬度"）
                    lng, lat = location.split(",")

                    return {
                        "lat": float(lat),      # 纬度
                        "lng": float(lng),     # 经度
                        "province": geocode.get("province", ""),   # 省份
                        "city": geocode.get("city", ""),         # 城市
                        "district": geocode.get("district", ""),   # 区县
                        "formatted_address": geocode.get("formatted_address", "")  # 格式化地址
                    }

            # 解析失败
            logger.warning(f"地址解析失败: {address}, response: {data}")
            return None

        except Exception as e:
            logger.error(f"地址解析异常: {address}, error: {e}")
            return None

    def batch_address_to_coord(self, addresses: list) -> dict:
        """
        批量将地址转换为坐标

        遍历每个地址调用 address_to_coord，如果失败则尝试添加"广州市"前缀重试

        Args:
            addresses: 地址列表，如 ["广州塔", "陈家祠", "白云山"]

        Returns:
            成功解析的地址字典：
            {
                "广州塔": {"lat": 23.130, "lng": 113.327, ...},
                "陈家祠": {"lat": 23.128, "lng": 113.256, ...},
                ...
            }
        """
        results = {}
        for addr in addresses:
            if addr:
                # 先直接尝试解析
                coord = self.address_to_coord(addr)
                if coord:
                    results[addr] = coord
                else:
                    # 尝试添加"广州市"前缀后重试
                    coord = self.address_to_coord(f"广州市{addr}")
                    if coord:
                        results[addr] = coord
        return results

    # ============================================================
    # 驾车路线
    # ============================================================

    def driving_route(self, start_coord: str, end_coord: str,
                      waypoints: list = None) -> Optional[dict]:
        """
        驾车路线规划

        使用高德驾车API：GET https://restapi.amap.com/v5/direction/driving

        Args:
            start_coord: 起点坐标，格式 "经度,纬度"，如 "113.327,23.130"
            end_coord: 终点坐标，格式 "经度,纬度"
            waypoints: 途经点坐标列表，如 ["113.256,23.128", "113.100,23.050"]

        Returns:
            成功返回：
            {
                "distance": 15000,   # 总距离（米）
                "duration": 1800,    # 总时间（秒）
                "steps": [           # 路线步骤
                    {
                        "instruction": "向东南方向行驶200米",
                        "road_name": "华夏路",
                        "distance": 200,
                        "duration": 30,
                        "start_point": {"lng": 113.327, "lat": 23.130},
                        "end_point": {"lng": 113.328, "lat": 23.131}
                    },
                    ...
                ]
            }
            失败返回 None
        """
        try:
            # 构造API请求参数
            params = {
                "key": self.api_key,
                "origin": start_coord,
                "destination": end_coord,
                "strategy": "0",  # 0=推荐模式（综合最优）
                "show_fields": "cost,polyline",  # 返回费用和坐标点
            }

            # 如果有途经点，添加到参数
            # 多个途经点用分号分隔
            if waypoints:
                params["waypoints"] = ";".join(waypoints)

            # 调用高德驾车API
            url = f"{DIRECTION_URL}/driving"
            response = requests.get(url, params=params, timeout=15)
            data = response.json()

            if data.get("status") == "1":
                route = data.get("route", {})
                paths = route.get("paths", [])  # 可能有多条路线，取第一条

                if paths:
                    first_path = paths[0]
                    # 注意：高德API v5 返回格式中，duration 在 cost 对象里，不在 path 直接字段
                    cost = first_path.get("cost", {})
                    return {
                        "distance": int(first_path.get("distance", 0)),
                        "duration": int(cost.get("duration", 0)),  # 从 cost 中取 duration
                        "steps": self._parse_driving_steps(first_path.get("steps", []))
                    }

            logger.warning(f"驾车路线规划失败: {data}")
            return None

        except Exception as e:
            logger.error(f"驾车路线规划异常: {e}")
            return None

    # ============================================================
    # 步行路线
    # ============================================================

    def walking_route(self, start_coord: str, end_coord: str) -> Optional[dict]:
        """
        步行路线规划

        使用高德步行API：GET https://restapi.amap.com/v5/direction/walking

        Args:
            start_coord: 起点坐标 "经度,纬度"
            end_coord: 终点坐标 "经度,纬度"

        Returns:
            同 driving_route，返回步行路线信息
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

    # ============================================================
    # 公交路线
    # ============================================================

    def transit_route(self, start_city: str, end_city: str,
                     start_coord: str = None, end_coord: str = None,
                     city_adcode: str = "440100") -> Optional[dict]:
        """
        公交路线规划

        使用高德公交API：GET https://restapi.amap.com/v5/direction/transit

        Args:
            start_city: 起点城市名
            end_city: 终点城市名
            city_adcode: 城市编码（默认440100=广州）

        Returns:
            公交路线信息
        """
        try:
            params = {
                "key": self.api_key,
                "origin": start_city,
                "destination": end_city,
                "city": city_adcode,
                "strategy": "0",  # 推荐路线
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

    # ============================================================
    # 内部方法：解析路线步骤
    # ============================================================

    def _parse_driving_steps(self, steps: list) -> list:
        """
        解析驾车路线步骤，提取关键信息

        高德返回的steps包含很多字段，我们只提取有用的：
        - instruction: 导航指示
        - road_name: 道路名称
        - distance: 距离
        - duration: 时间
        - polyline: 坐标点串（用于绑线）

        Args:
            steps: 高德API返回的steps数组

        Returns:
            简化后的步骤列表
        """
        result = []
        for step in steps:
            step_info = {
                "instruction": step.get("instruction", ""),
                "road_name": step.get("road_name", ""),
                "distance": int(step.get("distance", 0)),
                "duration": int(step.get("duration", 0)),
            }

            # 提取该步骤的起止坐标
            # polyline格式："lng1,lat1;lng2,lat2;..."用分号分隔
            if step.get("polyline"):
                coords = step["polyline"].split(";")
                if coords:
                    first = coords[0].split(",")  # 起点坐标
                    last = coords[-1].split(",")  # 终点坐标

                    # 转换为浮点数
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
        """
        解析步行路线步骤（同驾车）

        Args:
            steps: 高德API返回的steps数组

        Returns:
            简化后的步骤列表
        """
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

    # ============================================================
    # 完整路线规划（核心方法）
    # ============================================================

    def get_full_route_with_waypoints(self, start_address: str,
                                       destinations: list,
                                       travel_mode: str = "driving") -> dict:
        """
        获取包含多个途经点的完整驾车路线

        这是最常用的方法，实现了：
        1. 拆分合并的目的地（如 "故宫/颐和园/王府井" → 3个独立地点）
        2. 解析所有地址为坐标
        3. 逐段调用路线规划API
        4. 收集所有拐点坐标（用于前端绑线）

        Args:
            start_address: 起始地址，如 "广州市天河区"
            destinations: 目的地列表，如 ["广州塔", "陈家祠", "白云山"]
                     注意：可能包含合并格式如 ["故宫/颐和园/王府井"]
            travel_mode: 出行方式，"driving"(驾车)/"walking"(步行)/"transit"(公交)

        Returns:
            完整路线信息：
            {
                "success": True,
                "start_address": "广州市天河区",
                "destinations": ["广州塔", "陈家祠", "白云山"],  # 输入的目的地
                "waypoints": [  # 所有路线点（用于前端地图标记）
                    {"name": "广州市天河区", "lat": 23.130, "lng": 113.327},
                    {"name": "广州塔", "lat": 23.130, "lng": 113.327},
                    {"name": "陈家祠", "lat": 23.128, "lng": 113.256},
                    {"name": "白云山", "lat": 23.110, "lng": 113.100}
                ],
                "segments": [  # 路线分段
                    {
                        "from": "广州市天河区",
                        "to": "广州塔",
                        "distance": 5000,
                        "duration": 600,
                        "steps": [...]
                    },
                    ...
                ],
                "total_distance": 25000,  # 总距离（米）
                "total_duration": 3000,   # 总时间（秒）
                "polyline_points": [  # 拐点坐标（用于前端绑线）
                    [113.327, 23.130],
                    [113.328, 23.131],
                    [113.329, 23.132],
                    ...
                ]
            }
        """
        # 初始化返回结构
        result = {
            "success": False,
            "start_address": start_address,
            "destinations": destinations,  # 原始输入（可能包含合并格式）
            "waypoints": [],              # 所有点的坐标（用于地图标记）
            "segments": [],              # 路线分段（每段的详细信息）
            "total_distance": 0,          # 总距离
            "total_duration": 0           # 总时间
        }

        # ============================================================
        # 第1步：拆分合并的目的地
        # 例如 "故宫/颐和园/王府井" → ["故宫", "颐和园", "王府井"]
        # ============================================================
        expanded_destinations = []
        for dest in destinations:
            if '/' in dest:
                # 按斜杠拆分，去除空白
                parts = [p.strip() for p in dest.split('/') if p.strip()]
                expanded_destinations.extend(parts)
            else:
                expanded_destinations.append(dest.strip())

        logger.info(f"拆分后目的地: {expanded_destinations}")

        # ============================================================
        # 第2步：解析起始地址为坐标
        # ============================================================
        start_coord = self.address_to_coord(start_address)
        if not start_coord:
            result["error"] = f"无法解析起始地址: {start_address}"
            return result

        # 提取城市名前缀，用于给目的地添加城市名提高解析成功率
        # 例如：start_address="广州市天河区" → city_prefix="广州市"
        city_prefix = start_address.split('市')[0] + '市' if '市' in start_address else ''

        # ============================================================
        # 第3步：解析所有目的地为坐标
        # ============================================================
        dest_coords = []  # [{"name": "广州塔", "lat": ..., "lng": ...}, ...]

        for dest in expanded_destinations:
            # 先直接尝试解析
            coord = self.address_to_coord(dest)
            if coord:
                dest_coords.append({"name": dest, **coord})
            else:
                # 解析失败，尝试添加城市前缀重试
                # 例如："广州塔" → "广州市广州塔"
                coord = self.address_to_coord(f"{city_prefix}{dest}")
                if coord:
                    dest_coords.append({"name": dest, **coord})

        # 检查是否有任何目的地解析成功
        if not dest_coords:
            result["error"] = "无法解析任何目的地地址"
            return result

        # ============================================================
        # 第4步：构建waypoints，添加起点
        # ============================================================
        result["waypoints"].append({
            "name": start_address,
            "lat": start_coord["lat"],
            "lng": start_coord["lng"]
        })

        # ============================================================
        # 第5步：逐段规划路线（起点→第1个目的地→第2个→...→最后）
        # ============================================================
        current_point = f"{start_coord['lng']},{start_coord['lat']}"  # 格式："lng,lat"
        all_polyline_points = []  # 收集所有拐点坐标

        for dest in dest_coords:
            end_point = f"{dest['lng']},{dest['lat']}"

            # 根据出行方式选择路线规划方法
            if travel_mode == "driving":
                route = self.driving_route(current_point, end_point)
            elif travel_mode == "walking":
                route = self.walking_route(current_point, end_point)
            else:
                route = self.driving_route(current_point, end_point)

            # 如果路线规划成功
            if route:
                # 记录这段路线的信息
                result["segments"].append({
                    "from": result["waypoints"][-1]["name"],  # 上一个点
                    "to": dest["name"],                      # 当前目的地
                    "distance": route.get("distance", 0),
                    "duration": route.get("duration", 0),
                    "steps": route.get("steps", [])
                })

                # 累计总距离和时间
                result["total_distance"] += route.get("distance", 0)
                result["total_duration"] += route.get("duration", 0)

                # 收集拐点坐标（用于前端绑线）
                for seg in route.get("steps", []):
                    if "start_point" in seg:
                        all_polyline_points.append([
                            seg["start_point"]["lng"],
                            seg["start_point"]["lat"]
                        ])
                    if "end_point" in seg:
                        all_polyline_points.append([
                            seg["end_point"]["lng"],
                            seg["end_point"]["lat"]
                        ])

            # 更新当前点，继续规划下一段
            current_point = end_point

            # 添加目的地到waypoints
            result["waypoints"].append({
                "name": dest["name"],
                "lat": dest["lat"],
                "lng": dest["lng"]
            })

        # ============================================================
        # 第6步：返回结果
        # ============================================================
        result["success"] = True
        result["polyline_points"] = all_polyline_points  # 路线折线坐标

        return result


# ============================================================
# 单例模式
# ============================================================

_gaode_map_tools = None  # 全局单例实例


def get_gaode_map_tools() -> GaodeMapTools:
    """
    获取高德地图工具单例

    使用单例模式避免重复创建实例

    Returns:
        GaodeMapTools 实例
    """
    global _gaode_map_tools
    if _gaode_map_tools is None:
        _gaode_map_tools = GaodeMapTools()
    return _gaode_map_tools
