# -*- coding: utf-8 -*-
"""
Travel AI 性能测试脚本

测试内容：
1. 并发测试 - 多个用户同时发送请求
2. 响应时间统计 - 测量各阶段耗时
3. 节点执行分析 - 分析各Agent节点的执行时间
4. 测试报告生成 - 输出HTML格式的测试报告

使用方法：
    python performance_test.py

依赖安装：
    pip install aiohttp asyncio aiofiles
"""

import asyncio
import aiohttp
import time
import json
import sys
import os
from datetime import datetime
from typing import List, Dict, Optional
from dataclasses import dataclass, field
from collections import defaultdict

# 添加项目路径
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

# 测试配置
CONFIG = {
    "base_url": "http://localhost:8000",
    "concurrent_users": 8,           # 并发用户数
    "test_message": "去广州玩3天",    # 测试消息
    "timeout": 1200,                   # 请求超时时间（秒）
}


@dataclass
class NodeTiming:
    """节点计时信息"""
    name: str
    start_time: float
    end_time: Optional[float] = None

    @property
    def duration(self) -> float:
        if self.end_time:
            return self.end_time - self.start_time
        return 0


@dataclass
class RequestResult:
    """单次请求结果"""
    user_id: int
    session_id: str
    start_time: float
    end_time: Optional[float] = None
    success: bool = False
    error: Optional[str] = None
    first_response_time: float = 0     # 首次响应时间
    complete_time: float = 0           # 完整响应时间
    node_timings: List[NodeTiming] = field(default_factory=list)
    response_content: str = ""

    @property
    def total_duration(self) -> float:
        if self.end_time:
            return self.end_time - self.start_time
        return time.time() - self.start_time


class TravelAIPerformanceTest:
    """Travel AI 性能测试类"""

    def __init__(self, config: dict = None):
        self.config = config or CONFIG
        self.results: List[RequestResult] = []
        self.start_time = 0
        self.end_time = 0

    async def send_chat_request(self, session: aiohttp.ClientSession, user_id: int, message: str) -> RequestResult:
        """发送聊天请求"""
        result = RequestResult(
            user_id=user_id,
            session_id=f"perf_test_{user_id}_{int(time.time())}",
            start_time=time.time()
        )

        try:
            async with session.post(
                f"{self.config['base_url']}/api/chat/stream",
                json={
                    "content": message,
                    "user_id": user_id,
                    "session_id": int(result.session_id.split('_')[-1])  # 取时间戳作为 session_id
                },
                timeout=aiohttp.ClientTimeout(total=self.config['timeout']),
                headers={"Content-Type": "application/json"}
            ) as resp:
                if resp.status != 200:
                    result.error = f"HTTP {resp.status}"
                    return result

                # 用于跟踪当前节点
                current_node: Optional[NodeTiming] = None

                async for line in resp.content:
                    try:
                        line = line.decode('utf-8').strip()
                        if not line:
                            continue

                        # 解析SSE事件
                        if line.startswith('event:'):
                            event_type = line[6:].strip()
                            continue

                        if line.startswith('data:'):
                            data_str = line[5:].strip()
                            data = json.loads(data_str)

                            event_type = data.get('type', '')
                            elapsed = time.time() - result.start_time

                            # 记录首次响应时间
                            if result.first_response_time == 0:
                                result.first_response_time = elapsed

                            # 解析节点开始事件
                            if event_type == 'node_started':
                                node_name = data.get('data', {}).get('node', 'unknown')
                                node_timing = NodeTiming(
                                    name=node_name,
                                    start_time=time.time()
                                )
                                result.node_timings.append(node_timing)
                                current_node = node_timing

                            # 解析节点结束事件
                            elif event_type == 'node_finished':
                                if current_node and current_node.end_time is None:
                                    current_node.end_time = time.time()
                                    current_node = None

                            # 解析完成事件
                            elif event_type == 'agent_complete':
                                result.complete_time = elapsed
                                result.success = True
                                result.response_content = data.get('data', {}).get('content', '')[:200]
                                result.end_time = time.time()
                                return result

                    except json.JSONDecodeError:
                        continue
                    except Exception as e:
                        result.error = str(e)
                        continue

                # 如果循环结束还没收到完成事件
                if not result.success:
                    result.error = "未收到完成事件"
                    result.end_time = time.time()

        except asyncio.TimeoutError:
            result.error = "请求超时"
            result.end_time = time.time()
        except aiohttp.ClientError as e:
            result.error = f"连接错误: {str(e)}"
            result.end_time = time.time()
        except Exception as e:
            result.error = f"未知错误: {str(e)}"
            result.end_time = time.time()

        return result

    async def run_concurrent_test(self, num_users: int, message: str):
        """运行并发测试"""
        print(f"\n{'='*60}")
        print(f"开始并发测试: {num_users} 个并发用户")
        print(f"测试消息: {message}")
        print(f"开始时间: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
        print(f"{'='*60}\n")

        self.start_time = time.time()

        async with aiohttp.ClientSession() as session:
            tasks = [
                self.send_chat_request(session, i, message)
                for i in range(num_users)
            ]
            self.results = await asyncio.gather(*tasks)

        self.end_time = time.time()

    def print_summary(self):
        """打印测试结果摘要"""
        total_duration = self.end_time - self.start_time
        success_count = sum(1 for r in self.results if r.success)
        failed_count = len(self.results) - success_count

        # 计算各指标
        first_response_times = [r.first_response_time for r in self.results if r.first_response_time > 0]
        complete_times = [r.complete_time for r in self.results if r.complete_time > 0]
        total_times = [r.total_duration for r in self.results]

        print(f"\n{'='*60}")
        print("测试结果摘要")
        print(f"{'='*60}")
        print(f"总耗时: {total_duration:.2f} 秒")
        print(f"成功: {success_count}/{len(self.results)}")
        print(f"失败: {failed_count}/{len(self.results)}")
        print(f"\n--- 响应时间统计 ---")

        if first_response_times:
            print(f"首次响应时间:")
            print(f"  平均: {sum(first_response_times)/len(first_response_times):.2f}秒")
            print(f"  最短: {min(first_response_times):.2f}秒")
            print(f"  最长: {max(first_response_times):.2f}秒")

        if complete_times:
            print(f"完整响应时间:")
            print(f"  平均: {sum(complete_times)/len(complete_times):.2f}秒")
            print(f"  最短: {min(complete_times):.2f}秒")
            print(f"  最长: {max(complete_times):.2f}秒")

        if total_times:
            print(f"总耗时:")
            print(f"  平均: {sum(total_times)/len(total_times):.2f}秒")
            print(f"  最短: {min(total_times):.2f}秒")
            print(f"  最长: {max(total_times):.2f}秒")

        # 节点耗时分析
        print(f"\n--- 节点执行时间分析 ---")
        node_durations = defaultdict(list)
        for result in self.results:
            for node in result.node_timings:
                if node.end_time:
                    node_durations[node.name].append(node.duration)

        for node_name, durations in sorted(node_durations.items()):
            avg = sum(durations) / len(durations)
            print(f"  {node_name}: 平均 {avg:.2f}秒, 样本数 {len(durations)}")

        # 失败详情
        if failed_count > 0:
            print(f"\n--- 失败详情 ---")
            for i, result in enumerate(self.results):
                if not result.success:
                    print(f"  用户{i}: {result.error}")

        print(f"\n{'='*60}")

    def generate_html_report(self, filename: str = "performance_report.html"):
        """生成HTML测试报告"""
        total_duration = self.end_time - self.start_time
        success_count = sum(1 for r in self.results if r.success)
        failed_count = len(self.results) - success_count

        first_response_times = [r.first_response_time for r in self.results if r.first_response_time > 0]
        complete_times = [r.complete_time for r in self.results if r.complete_time > 0]
        total_times = [r.total_duration for r in self.results]

        # 安全计算平均值，避免除零
        def safe_avg(lst):
            return sum(lst) / len(lst) if lst else 0
        def safe_min(lst):
            return min(lst) if lst else 0
        def safe_max(lst):
            return max(lst) if lst else 0

        # 节点耗时
        node_durations = defaultdict(list)
        for result in self.results:
            for node in result.node_timings:
                if node.end_time:
                    node_durations[node.name].append(node.duration)

        node_stats = []
        for node_name, durations in sorted(node_durations.items()):
            node_stats.append({
                "name": node_name,
                "avg": safe_avg(durations),
                "min": safe_min(durations),
                "max": safe_max(durations),
                "count": len(durations)
            })

        html = f"""<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <title>Travel AI 性能测试报告</title>
    <style>
        body {{ font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; margin: 40px; background: #f5f5f5; }}
        .container {{ max-width: 1200px; margin: 0 auto; }}
        h1 {{ color: #1e40af; border-bottom: 3px solid #3b82f6; padding-bottom: 10px; }}
        h2 {{ color: #374151; margin-top: 30px; }}
        .summary {{ background: white; padding: 20px; border-radius: 10px; box-shadow: 0 2px 8px rgba(0,0,0,0.1); }}
        .metric {{ display: flex; gap: 40px; margin: 15px 0; }}
        .metric-item {{ text-align: center; }}
        .metric-value {{ font-size: 28px; font-weight: bold; color: #3b82f6; }}
        .metric-label {{ color: #6b7280; font-size: 14px; }}
        .success {{ color: #10b981; }}
        .failed {{ color: #ef4444; }}
        table {{ width: 100%; border-collapse: collapse; margin: 15px 0; background: white; border-radius: 8px; overflow: hidden; }}
        th, td {{ padding: 12px 15px; text-align: left; border-bottom: 1px solid #e5e7eb; }}
        th {{ background: #f3f4f6; font-weight: 600; color: #374151; }}
        tr:hover {{ background: #f9fafb; }}
        .tag {{ display: inline-block; padding: 4px 10px; border-radius: 20px; font-size: 12px; }}
        .tag-success {{ background: #d1fae5; color: #065f46; }}
        .tag-failed {{ background: #fee2e2; color: #991b1b; }}
        .chart {{ margin: 20px 0; }}
        .info-box {{ background: #eff6ff; border-left: 4px solid #3b82f6; padding: 15px; margin: 15px 0; border-radius: 4px; }}
    </style>
</head>
<body>
    <div class="container">
        <h1>📊 Travel AI 性能测试报告</h1>
        <p>生成时间: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}</p>

        <div class="summary">
            <h2>测试概览</h2>
            <div class="metric">
                <div class="metric-item">
                    <div class="metric-value">{total_duration:.1f}秒</div>
                    <div class="metric-label">总耗时</div>
                </div>
                <div class="metric-item">
                    <div class="metric-value">{self.config['concurrent_users']}</div>
                    <div class="metric-label">并发用户</div>
                </div>
                <div class="metric-item">
                    <div class="metric-value success">{success_count}</div>
                    <div class="metric-label">成功</div>
                </div>
                <div class="metric-item">
                    <div class="metric-value failed">{failed_count}</div>
                    <div class="metric-label">失败</div>
                </div>
            </div>
        </div>

        <div class="summary">
            <h2>响应时间统计</h2>"""

        if first_response_times:
            html += f"""
            <table>
                <tr>
                    <th>指标</th>
                    <th>首次响应</th>
                    <th>完整响应</th>
                    <th>总耗时</th>
                </tr>
                <tr>
                    <td>平均</td>
                    <td>{safe_avg(first_response_times):.2f}秒</td>
                    <td>{safe_avg(complete_times):.2f}秒</td>
                    <td>{safe_avg(total_times):.2f}秒</td>
                </tr>
                <tr>
                    <td>最短</td>
                    <td>{safe_min(first_response_times):.2f}秒</td>
                    <td>{safe_min(complete_times):.2f}秒</td>
                    <td>{safe_min(total_times):.2f}秒</td>
                </tr>
                <tr>
                    <td>最长</td>
                    <td>{safe_max(first_response_times):.2f}秒</td>
                    <td>{safe_max(complete_times):.2f}秒</td>
                    <td>{safe_max(total_times):.2f}秒</td>
                </tr>
            </table>"""
        else:
            html += """<p>无有效响应数据</p>"""

        html += """
        </div>

        <div class="summary">
            <h2>节点执行时间分析</h2>"""

        if node_stats:
            html += """
            <table>
                <tr>
                    <th>节点名称</th>
                    <th>平均耗时</th>
                    <th>最短</th>
                    <th>最长</th>
                    <th>样本数</th>
                </tr>"""
            for node in node_stats:
                html += f"""
                <tr>
                    <td>{node['name']}</td>
                    <td>{node['avg']:.2f}秒</td>
                    <td>{node['min']:.2f}秒</td>
                    <td>{node['max']:.2f}秒</td>
                    <td>{node['count']}</td>
                </tr>"""
            html += "</table>"
        else:
            html += "<p>无节点执行数据</p>"

        html += """
        </div>

        <div class="summary">
            <h2>详细请求结果</h2>
            <table>
                <tr>
                    <th>用户ID</th>
                    <th>状态</th>
                    <th>首次响应</th>
                    <th>完整响应</th>
                    <th>总耗时</th>
                    <th>错误信息</th>
                </tr>"""

        for result in self.results:
            status = '<span class="tag tag-success">成功</span>' if result.success else '<span class="tag tag-failed">失败</span>'
            error = result.error or "-"
            html += f"""
                <tr>
                    <td>用户{result.user_id}</td>
                    <td>{status}</td>
                    <td>{result.first_response_time:.2f}秒</td>
                    <td>{result.complete_time:.2f}秒</td>
                    <td>{result.total_duration:.2f}秒</td>
                    <td>{error}</td>
                </tr>"""

        html += """
            </table>
        </div>

        <div class="info-box">
            <h3>💡 性能优化建议</h3>
            <ul>
                <li>如果 Researcher 节点耗时过长，考虑优化搜索策略或增加缓存</li>
                <li>如果 Planner 节点耗时过长，检查 LLM 调用是否超时</li>
                <li>如果 Critic 循环次数过多，检查评分阈值是否合理</li>
                <li>如果首次响应时间过长，检查 Intent Recognition 节点</li>
            </ul>
        </div>
    </div>
</body>
</html>"""

        with open(filename, 'w', encoding='utf-8') as f:
            f.write(html)

        print(f"\n📄 HTML 报告已生成: {filename}")
        return filename


async def main():
    """主函数"""
    print("🚀 Travel AI 性能测试")
    print("=" * 50)

    # 检查服务是否可用
    try:
        async with aiohttp.ClientSession() as session:
            async with session.get(f"{CONFIG['base_url']}/api/health", timeout=aiohttp.ClientTimeout(total=5)) as resp:
                if resp.status != 200:
                    print(f"⚠️ 服务可能未启动或不可用，状态码: {resp.status}")
                else:
                    print("✅ 服务连接正常")
    except Exception as e:
        print(f"❌ 无法连接到服务: {e}")
        print(f"请确保 Travel AI 服务正在运行在 {CONFIG['base_url']}")
        return

    # 创建测试实例
    tester = TravelAIPerformanceTest(CONFIG)

    # 运行并发测试
    await tester.run_concurrent_test(
        num_users=CONFIG['concurrent_users'],
        message=CONFIG['test_message']
    )

    # 打印结果
    tester.print_summary()

    # 生成HTML报告
    report_file = tester.generate_html_report()
    print(f"\n🎉 测试完成！")
    print(f"   查看报告: {report_file}")


if __name__ == "__main__":
    print("注意: 请确保 Travel AI 服务已启动在 http://localhost:8000")
    print("按 Ctrl+C 取消\n")

    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        print("\n\n测试已取消")
    except ImportError as e:
        print(f"\n❌ 缺少依赖: {e}")
        print("请运行: pip install aiohttp")
