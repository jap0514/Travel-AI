# ====================================================================
#  IK + Pinyin 插件下载脚本（Windows PowerShell）
#  路径：es-scripts/download-plugins.ps1
#
#  用法：
#    1. PowerShell 进入 D:\毕业设计\Travel\AI_travel\es-scripts 目录
#    2. 执行 .\download-plugins.ps1
#    3. 下载完成后执行 docker compose up -d elasticsearch kibana
# ====================================================================

# 插件版本必须与 ES 服务端版本严格一致（8.13.4）
$ES_VERSION = "8.13.4"

# 插件保存目录（与 docker-compose.yml 中 ./es-scripts/plugins 映射一致）
$PLUGIN_DIR = Join-Path $PSScriptRoot "plugins"

# 创建目录
if (-not (Test-Path $PLUGIN_DIR)) {
    New-Item -ItemType Directory -Path $PLUGIN_DIR | Out-Null
}

# IK 分词插件（中文分词）
$IK_URL = "https://github.com/medcl/elasticsearch-analysis-ik/releases/download/v$ES_VERSION/elasticsearch-analysis-ik-$ES_VERSION.zip"
$IK_FILE = Join-Path $PLUGIN_DIR "elasticsearch-analysis-ik-$ES_VERSION.zip"

# Pinyin 分词插件（拼音搜索）
$PINYIN_URL = "https://github.com/medcl/elasticsearch-analysis-pinyin/releases/download/v$ES_VERSION/elasticsearch-analysis-pinyin-$ES_VERSION.zip"
$PINYIN_FILE = Join-Path $PLUGIN_DIR "elasticsearch-analysis-pinyin-$ES_VERSION.zip"

# 下载函数（带进度条）
function Download-Plugin {
    param([string]$Url, [string]$Dest)
    if (Test-Path $Dest) {
        Write-Host "[SKIP] 已存在: $Dest" -ForegroundColor Yellow
        return
    }
    Write-Host "[DOWN] 下载中: $Url" -ForegroundColor Cyan
    try {
        [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
        Invoke-WebRequest -Uri $Url -OutFile $Dest -UseBasicParsing
        Write-Host "[OK] 下载完成: $Dest" -ForegroundColor Green
    } catch {
        Write-Host "[FAIL] 下载失败: $_" -ForegroundColor Red
        Write-Host "      请手动下载到: $Dest" -ForegroundColor Red
    }
}

Write-Host "=== 下载 ES $ES_VERSION 所需分词插件 ===" -ForegroundColor Cyan
Download-Plugin -Url $IK_URL -Dest $IK_FILE
Download-Plugin -Url $PINYIN_URL -Dest $PINYIN_FILE

Write-Host ""
Write-Host "=== 插件目录内容 ===" -ForegroundColor Cyan
Get-ChildItem $PLUGIN_DIR | Format-Table Name, Length -AutoSize

Write-Host ""
Write-Host "下一步：执行 docker compose up -d elasticsearch kibana" -ForegroundColor Green
Write-Host "Kibana 访问地址: http://localhost:5601" -ForegroundColor Green
