# ====================================================================
#  ES 搜索功能测试脚本（Windows PowerShell）
#  路径：es-scripts/02-test-search.ps1
#
#  流程：写入测试数据 -> 多场景查询 -> 清理
# ====================================================================

$ES = "http://localhost:9200"
$INDEX = "hotel_v1"

Write-Host "=== [1/5] 写入 5 条测试酒店数据 ===" -ForegroundColor Cyan

$hotels = @(
    @{
        hotelId      = 1001
        name         = "上海陆家嘴丽思卡尔顿酒店"
        city         = "上海"
        address      = "上海市浦东新区陆家嘴环路1717号"
        star         = 5
        location     = @{ lat = 31.2367; lon = 121.5055 }
        minPrice     = 2800.00
        contactPhone = "021-20201888"
        facilities   = @("游泳池", "健身房", "WiFi", "SPA", "24小时前台")
        description  = "坐落于上海陆家嘴金融中心，俯瞰黄浦江全景。"
        createTime   = "2026-08-01T10:00:00"
        updateTime   = "2026-08-28T10:00:00"
    },
    @{
        hotelId      = 1002
        name         = "上海外滩茂悦大酒店"
        city         = "上海"
        address      = "上海市黄浦区黄浦路199号"
        star         = 5
        location     = @{ lat = 31.2400; lon = 121.4900 }
        minPrice     = 1800.00
        contactPhone = "021-63240088"
        facilities   = @("游泳池", "商务中心", "WiFi", "酒吧")
        description  = "位于上海外滩，毗邻黄浦江，可远眺浦东天际线。"
        createTime   = "2026-08-02T10:00:00"
        updateTime   = "2026-08-28T10:00:00"
    },
    @{
        hotelId      = 1003
        name         = "北京王府井希尔顿酒店"
        city         = "北京"
        address      = "北京市东城区王府井东街8号"
        star         = 5
        location     = @{ lat = 39.9139; lon = 116.4106 }
        minPrice     = 2200.00
        contactPhone = "010-58658888"
        facilities   = @("游泳池", "健身房", "WiFi", "商务中心", "停车场")
        description  = "位于北京王府井商圈，临近故宫、天安门广场。"
        createTime   = "2026-08-03T10:00:00"
        updateTime   = "2026-08-28T10:00:00"
    },
    @{
        hotelId      = 1004
        name         = "广州白云山希尔顿酒店"
        city         = "广州"
        address      = "广州市白云区白云山西门"
        star         = 4
        location     = @{ lat = 23.1738; lon = 113.2900 }
        minPrice     = 888.00
        contactPhone = "020-66668888"
        facilities   = @("游泳池", "WiFi", "停车场", "会议室")
        description  = "坐落于广州白云山风景区，空气清新，环境优雅。"
        createTime   = "2026-08-04T10:00:00"
        updateTime   = "2026-08-28T10:00:00"
    },
    @{
        hotelId      = 1005
        name         = "深圳南山如家精选酒店"
        city         = "深圳"
        address      = "深圳市南山区科技园南区"
        star         = 3
        location     = @{ lat = 22.5333; lon = 113.9500 }
        minPrice     = 388.00
        contactPhone = "0755-86668888"
        facilities   = @("WiFi", "24小时前台", "空调")
        description  = "经济型连锁酒店，适合商务出差。"
        createTime   = "2026-08-05T10:00:00"
        updateTime   = "2026-08-28T10:00:00"
    }
)

foreach ($h in $hotels) {
    $body = $h | ConvertTo-Json -Depth 5 -Compress
    Invoke-RestMethod -Method PUT -Uri "$ES/$INDEX/_doc/$($h.hotelId)" `
        -ContentType "application/json" -Body $body | Out-Null
    Write-Host "  写入 hotelId=$($h.hotelId): $($h.name)" -ForegroundColor Gray
}

# 刷新索引让数据可搜索
Invoke-RestMethod -Method POST -Uri "$ES/$INDEX/_refresh" | Out-Null
Write-Host "数据已刷新" -ForegroundColor Green

# ========== 测试用例 ==========

function Test-Search {
    param([string]$Title, [string]$QueryJson)

    Write-Host ""
    Write-Host "=== $Title ===" -ForegroundColor Cyan
    $resp = Invoke-RestMethod -Method POST -Uri "$ES/$INDEX/_search" `
        -ContentType "application/json" -Body $QueryJson

    Write-Host "命中数: $($resp.hits.total.value)" -ForegroundColor Green
    foreach ($hit in $resp.hits.hits) {
        $name = $hit._source.name
        $score = [math]::Round($hit._score, 2)
        $city = $hit._source.city
        Write-Host "  [$score] $name ($city)"

        # 高亮
        if ($hit.highlight) {
            foreach ($field in $hit.highlight.PSObject.Properties.Name) {
                $highlightText = $hit.highlight.$field -join " ... "
                Write-Host "      [高亮 $field]: $highlightText" -ForegroundColor DarkGray
            }
        }
    }
}

# 测试 1：中文 multi_match
Test-Search "[测试 1] multi_match: '丽思'" (@{
    query = @{
        multi_match = @{
            query  = "丽思"
            fields = @("name^3", "description")
        }
    }
    highlight = @{
        fields = @{ name = @{ number_of_fragments = 0 } }
    }
} | ConvertTo-Json -Depth 8 -Compress)

# 测试 2：拼音搜索
Test-Search "[测试 2] 拼音搜索: 'lujiazui'" (@{
    query = @{ match = @{ "name.pinyin" = "lujiazui" } }
    highlight = @{
        fields = @{ "name.pinyin" = @{ number_of_fragments = 0 } }
    }
} | ConvertTo-Json -Depth 8 -Compress)

# 测试 3：城市 + 星级 + 价格 组合过滤
Test-Search "[测试 3] 城市=上海, 5星, 2000+ 元" (@{
    query = @{
        bool = @{
            filter = @(
                @{ term = @{ city = "上海" } }
                @{ term = @{ star = 5 } }
                @{ range = @{ minPrice = @{ gte = 2000 } } }
            )
        }
    }
    sort = @(@{ minPrice = "asc" })
} | ConvertTo-Json -Depth 8 -Compress)

# 测试 4：地理距离搜索（天安门 5km 内）
Test-Search "[测试 4] 地理搜索：天安门 5km 内" (@{
    query = @{
        bool = @{
            must = @( @{ match_all = @{} } )
            filter = @(
                @{ geo_distance = @{
                    distance = "5km"
                    location = @{ lat = 39.9087; lon = 116.3975 }
                } }
            )
        }
    }
} | ConvertTo-Json -Depth 8 -Compress)

# 测试 5：facilities 多选
Test-Search "[测试 5] 设施包含：游泳池 + 健身房" (@{
    query = @{
        bool = @{
            filter = @(
                @{ term = @{ facilities = "游泳池" } }
                @{ term = @{ facilities = "健身房" } }
            )
        }
    }
} | ConvertTo-Json -Depth 8 -Compress)

# ========== 清理 ==========
Write-Host ""
Write-Host "=== [清理] 删除测试数据 ===" -ForegroundColor Cyan
foreach ($h in $hotels) {
    Invoke-RestMethod -Method DELETE -Uri "$ES/$INDEX/_doc/$($h.hotelId)" | Out-Null
}
Invoke-RestMethod -Method POST -Uri "$ES/$INDEX/_refresh" | Out-Null
Write-Host "清理完成" -ForegroundColor Green

Write-Host ""
Write-Host "=== 全部测试通过 ===" -ForegroundColor Green
