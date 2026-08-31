# ====================================================================
#  创建 hotel_v1 ES 索引脚本（Windows PowerShell）
#  路径：es-scripts/01-create-index.ps1
#
#  用法：PowerShell 进入 D:\毕业设计\Travel\AI_travel\es-scripts
#        执行 .\01-create-index.ps1
# ====================================================================

# ES 地址（与 docker-compose 端口映射保持一致）
$ES = "http://localhost:9200"

# mapping 文件路径
$MAPPING_FILE = Join-Path $PSScriptRoot "hotel_v1.json"

# ---------- 1. 检查 ES 集群状态 ----------
Write-Host "=== [1/4] 检查 ES 集群状态 ===" -ForegroundColor Cyan
try {
    $cluster = Invoke-RestMethod -Uri "$ES/_cluster/health?pretty" -TimeoutSec 5
    Write-Host "集群名:   $($cluster.cluster_name)" -ForegroundColor Green
    Write-Host "集群状态: $($cluster.status)" -ForegroundColor Green
    Write-Host "节点数:   $($cluster.number_of_nodes)" -ForegroundColor Green

    if ($cluster.status -notin @("green", "yellow")) {
        Write-Host "ES 状态异常，请先启动: docker compose up -d elasticsearch" -ForegroundColor Red
        exit 1
    }
} catch {
    Write-Host "ES 未响应: $_" -ForegroundColor Red
    Write-Host "请确认 docker 容器已启动: docker compose ps elasticsearch" -ForegroundColor Red
    exit 1
}

# ---------- 2. 检查 IK + Pinyin 插件 ----------
Write-Host ""
Write-Host "=== [2/4] 检查分词插件 ===" -ForegroundColor Cyan
$plugins = Invoke-RestMethod -Uri "$ES/_cat/plugins?format=json&h=component"
$pluginNames = $plugins | ForEach-Object { $_.component }
Write-Host "已安装插件: $($pluginNames -join ', ')"

$hasIK = $pluginNames -contains "analysis-ik"
$hasPinyin = $pluginNames -contains "analysis-pinyin"
if (-not $hasIK) {
    Write-Host "[WARN] 未检测到 analysis-ik 插件，中文分词不可用" -ForegroundColor Yellow
}
if (-not $hasPinyin) {
    Write-Host "[WARN] 未检测到 analysis-pinyin 插件，拼音搜索不可用" -ForegroundColor Yellow
}

# ---------- 3. 删除已存在的 hotel_v1 索引（幂等）----------
Write-Host ""
Write-Host "=== [3/4] 删除已存在的 hotel_v1 索引 ===" -ForegroundColor Cyan
try {
    Invoke-RestMethod -Method DELETE -Uri "$ES/hotel_v1" | Out-Null
    Write-Host "已删除旧索引" -ForegroundColor Green
} catch {
    $statusCode = $_.Exception.Response.StatusCode.value__
    if ($statusCode -eq 404) {
        Write-Host "索引不存在，跳过删除" -ForegroundColor Yellow
    } else {
        throw
    }
}

# ---------- 4. 创建 hotel_v1 索引 ----------
Write-Host ""
Write-Host "=== [4/4] 创建 hotel_v1 索引 ===" -ForegroundColor Cyan
$mappingJson = Get-Content -Path $MAPPING_FILE -Raw -Encoding UTF8

try {
    $resp = Invoke-RestMethod -Method PUT -Uri "$ES/hotel_v1" `
        -ContentType "application/json; charset=utf-8" `
        -Body $mappingJson
    Write-Host "索引创建成功！ack=$($resp.acknowledged), shards_acknowledged=$($resp.shards_acknowledged)" -ForegroundColor Green
} catch {
    Write-Host "索引创建失败: $_" -ForegroundColor Red
    exit 1
}

# ---------- 5. 验证 ----------
Write-Host ""
Write-Host "=== [验证] 查询索引 mapping ===" -ForegroundColor Cyan
$m = Invoke-RestMethod -Uri "$ES/hotel_v1/_mapping?pretty"
$fields = $m.hotel_v1.mappings.properties.PSObject.Properties.Name
Write-Host "字段列表: $($fields -join ', ')" -ForegroundColor Green

# 测试 IK 分词
Write-Host ""
Write-Host "=== [测试] IK 分词效果 ===" -ForegroundColor Cyan
$analyzeBody = @{
    analyzer = "ik_max_word"
    text     = "上海陆家嘴丽思卡尔顿酒店"
} | ConvertTo-Json

$tokens = Invoke-RestMethod -Method POST -Uri "$ES/hotel_v1/_analyze" `
    -ContentType "application/json" -Body $analyzeBody
$tokens.tokens | ForEach-Object {
    Write-Host "  分词: $($_.token)" -ForegroundColor Gray
}

Write-Host ""
Write-Host "=== 完成 ===" -ForegroundColor Green
Write-Host "Kibana: http://localhost:5601  -> Dev Tools" -ForegroundColor Green
Write-Host "  GET hotel_v1/_search" -ForegroundColor Gray
