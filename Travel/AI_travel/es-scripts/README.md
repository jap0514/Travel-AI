# es-scripts · Elasticsearch 启动与验证脚本

本目录包含 Elasticsearch 的所有配置、插件、索引脚本。

## 📁 目录结构

```
es-scripts/
├── README.md                       本文件
├── es-config/
│   └── elasticsearch.yml           ES 服务端配置（挂载到容器内）
├── plugins/                        IK + Pinyin 插件 zip（需手动下载）
├── hotel_v1.json                   hotel 索引 mapping 定义
├── download-plugins.ps1            插件下载脚本（Windows）
├── 01-create-index.ps1             创建 hotel_v1 索引
└── 02-test-search.ps1              5 个搜索场景测试
```

## 🚀 完整启动流程

### 第 1 步：下载分词插件

```powershell
cd D:\毕业设计\Travel\AI_travel\es-scripts
.\download-plugins.ps1
```

下载完成后 `plugins/` 目录下会有两个 zip：
- `elasticsearch-analysis-ik-8.13.4.zip`
- `elasticsearch-analysis-pinyin-8.13.4.zip`

### 第 2 步：启动 ES + Kibana

回到项目根目录：

```powershell
cd D:\毕业设计\Travel\AI_travel
docker compose up -d elasticsearch kibana
```

等待约 30-60 秒。检查日志：

```powershell
docker compose logs -f elasticsearch
```

看到 `"started"` 或 `"ready"` 后 Ctrl+C 退出。

### 第 3 步：验证插件安装

```powershell
curl http://localhost:9200/_cat/plugins
```

应输出：
```
travel_es_node_1 analysis-ik       8.13.4
travel_es_node_1 analysis-pinyin   8.13.4
```

### 第 4 步：创建索引

```powershell
cd es-scripts
.\01-create-index.ps1
```

### 第 5 步：测试搜索

```powershell
.\02-test-search.ps1
```

应看到 5 个测试场景全部命中。

## 🔧 故障排查

| 现象 | 排查 |
|------|------|
| `connection refused` | ES 未启动：`docker compose ps` 看状态 |
| `plugin version mismatch` | 插件版本需与 ES 一致（8.13.4）|
| `IK 分词没效果` | 检查 `_cat/plugins` 确认 analysis-ik 已加载 |
| Kibana 一直转圈 | 等 ES 变 green/yellow 后 Kibana 自动重连 |
| `max_map_count too low` | WSL 下执行：`wsl -d docker-desktop sysctl -w vm.max_map_count=262144` |

## 📍 服务地址

| 服务 | 地址 | 用途 |
|------|------|------|
| ES HTTP | http://localhost:9200 | REST API |
| Kibana | http://localhost:5601 | Dev Tools / Dashboard |
| ES 集群健康 | http://localhost:9200/_cluster/health | 健康检查 |
| Java Bean | `localhost:9999/es/health` | 应用层联通验证 |
