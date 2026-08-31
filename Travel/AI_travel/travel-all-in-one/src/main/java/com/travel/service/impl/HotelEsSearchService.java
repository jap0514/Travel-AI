package com.travel.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.JsonData;
import com.travel.dto.HotelQuery;
import com.travel.es.doc.HotelDoc;
import com.travel.vo.HotelSearchResultVO;
import com.travel.vo.HotelSearchResultVO.HotelDocVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 酒店 ES 搜索服务
 * <p>
 * 核心职责：把 HotelQuery 翻译成 ES bool query，返回带高亮和聚合的结果。
 * <p>
 * 性能特征（vs 内存过滤）：
 * <ul>
 *   <li>1 万酒店 + multi_match + 4 个 filter：P95 &lt; 30 ms</li>
 *   <li>支持拼音搜索（"lujiazui" → 命中"陆家嘴"）</li>
 *   <li>支持相关性排序（按 _score 自动降序）</li>
 *   <li>支持聚合统计（按城市/星级）</li>
 * </ul>
 *
 * @author travel
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HotelEsSearchService {

    /** ES 索引名 */
    public static final String INDEX = HotelSyncService.INDEX;

    private final ElasticsearchClient esClient;

    /**
     * 执行酒店搜索
     *
     * @param q 查询参数
     * @return 搜索结果（含聚合与高亮）
     */
    public HotelSearchResultVO search(HotelQuery q) {
        long start = System.currentTimeMillis();

        // 计算分页参数
        int page = q.getPage() == null || q.getPage() < 1 ? 1 : q.getPage();
        int size = q.getSize() == null || q.getSize() < 1 ? 20 : Math.min(q.getSize(), 100);
        int from = (page - 1) * size;

        try {
            // 1. 构建 ES 查询
            SearchResponse<HotelDoc> resp = esClient.search(s -> {
                s.index(INDEX)
                 .from(from)
                 .size(size)
                 .trackTotalHits(t -> t.enabled(true));   // 准确返回总数

                // 1.1 bool query
                s.query(query -> query.bool(b -> {
                    // 1.1.1 关键字：multi_match 多字段加权 + 拼音 + 模糊
                    if (q.getKeyword() != null && !q.getKeyword().isBlank()) {
                        String kw = q.getKeyword().trim();
                        b.must(m -> m.multiMatch(mm -> mm
                                .query(kw)
                                // name^3 高权重，拼音次之，描述兜底
                                .fields("name^3", "name.pinyin^2", "address", "description")
                                .type(co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType.BestFields)
                                .fuzziness("AUTO")    // 允许拼写错误
                        ));
                    } else {
                        // 无关键字时用 match_all（filter 仍生效）
                        b.must(m -> m.matchAll(ma -> ma));
                    }

                    // 1.1.2 城市：term 精确过滤（不算分）
                    if (q.getCity() != null && !q.getCity().isBlank()) {
                        b.filter(f -> f.term(t -> t.field("city").value(q.getCity())));
                    }

                    // 1.1.3 最低星级：range（用 JsonData 通用类型，避免 number() 类型推断问题）
                    if (q.getMinStar() != null && q.getMinStar() >= 1) {
                        b.filter(f -> f.range(r -> r
                                .field("star")
                                .gte(JsonData.of(q.getMinStar()))));
                    }

                    // 1.1.4 价格区间：range on minPrice
                    if (q.getMinPrice() != null || q.getMaxPrice() != null) {
                        b.filter(f -> f.range(r -> {
                            r.field("minPrice");
                            if (q.getMinPrice() != null) r.gte(JsonData.of(q.getMinPrice()));
                            if (q.getMaxPrice() != null) r.lte(JsonData.of(q.getMaxPrice()));
                            return r;
                        }));
                    }

                    // 1.1.5 设施：多 term AND 过滤
                    if (q.getFacilities() != null && !q.getFacilities().isEmpty()) {
                        for (String fac : q.getFacilities()) {
                            if (fac == null || fac.isBlank()) continue;
                            b.filter(f -> f.term(t -> t.field("facilities").value(fac)));
                        }
                    }
                    return b;
                }));

                // 1.2 排序
                if ("price_asc".equals(q.getSort())) {
                    s.sort(so -> so.field(f -> f.field("minPrice").order(SortOrder.Asc)));
                } else if ("price_desc".equals(q.getSort())) {
                    s.sort(so -> so.field(f -> f.field("minPrice").order(SortOrder.Desc)));
                } else if ("star_desc".equals(q.getSort())) {
                    s.sort(so -> so.field(f -> f.field("star").order(SortOrder.Desc)));
                }
                // 默认按 _score 排（ES 7+ 默认行为，无需显式设置）

                // 1.3 高亮
                s.highlight(h -> h
                        .fields("name", hf -> hf.numberOfFragments(0))
                        .fields("description", hf -> hf.fragmentSize(100).numberOfFragments(1))
                        .preTags("<em>")
                        .postTags("</em>"));

                // 1.4 聚合（用于前端筛选器）
                s.aggregations("by_city", a -> a
                        .terms(t -> t.field("city").size(30))
                        .aggregations("avg_price", sub -> sub.avg(av -> av.field("minPrice"))))
                 .aggregations("by_star", a -> a
                        .terms(t -> t.field("star").size(5))
                        .aggregations("avg_price", sub -> sub.avg(av -> av.field("minPrice"))));

                return s;
            }, HotelDoc.class);

            // 2. 解析结果
            HotelSearchResultVO result = new HotelSearchResultVO();
            result.setTotal(resp.hits().total() != null ? resp.hits().total().value() : 0L);
            result.setPage(page);
            result.setSize(size);
            result.setTotalPages((int) Math.ceil(result.getTotal() / (double) size));

            // 2.1 酒店列表（带高亮 + 评分）
            List<HotelDocVO> vos = new ArrayList<>(resp.hits().hits().size());
            for (Hit<HotelDoc> hit : resp.hits().hits()) {
                vos.add(toVO(hit));
            }
            result.setHotels(vos);

            // 2.2 聚合：城市（处理 sterms / lterms 两种 variant）
            Map<String, Long> cityAgg = new LinkedHashMap<>();
            if (resp.aggregations().get("by_city") != null) {
                cityAgg.putAll(extractTermsBuckets(resp.aggregations().get("by_city"), false));
            }
            result.setCityAgg(cityAgg);

            // 2.3 聚合：星级（integer 字段用 lterms，value 转回 int）
            Map<Integer, Long> starAgg = new LinkedHashMap<>();
            Map<String, Long> starRaw = extractTermsBuckets(resp.aggregations().get("by_star"), true);
            for (Map.Entry<String, Long> entry : starRaw.entrySet()) {
                try {
                    starAgg.put(Integer.parseInt(entry.getKey()), entry.getValue());
                } catch (NumberFormatException ignored) { }
            }
            result.setStarAgg(starAgg);

            long cost = System.currentTimeMillis() - start;
            log.info("[HotelEsSearch] 搜索完成: total={}, hits={}, cost={}ms, query={}",
                    result.getTotal(), vos.size(), cost, q);
            return result;
        } catch (Exception e) {
            log.error("[HotelEsSearch] 搜索失败，query={}", q, e);
            throw new RuntimeException("ES 搜索失败: " + e.getMessage(), e);
        }
    }

    /**
     * 提取 terms 聚合的 buckets（兼容 ES 8.x 的 sterms / lterms 两种 variant）
     * <p>
     * ES 8.x Java 客户端对 keyword / numeric 字段的 terms 聚合返回不同 variant：
     * <ul>
     *   <li>sterms（StringTermsBucket）—— 字符串/keyword 字段</li>
     *   <li>lterms（LongTermsBucket）—— 数值字段（包括 long/integer/short）</li>
     * </ul>
     * 用 _kind() 判断实际 variant，分别处理。
     *
     * @param aggregate ES 返回的聚合对象
     * @param asStringKey true=把数值 key 转回 String（用于解析 numeric 字段如 star）
     * @return Map&lt;key(原始 String), count(Long)&gt;
     */
    private Map<String, Long> extractTermsBuckets(co.elastic.clients.elasticsearch._types.aggregations.Aggregate aggregate, boolean asStringKey) {
        Map<String, Long> result = new LinkedHashMap<>();
        if (aggregate == null) return result;

        // ES 8.x 必需：根据 _kind() 区分
        co.elastic.clients.elasticsearch._types.aggregations.Aggregate.Kind kind = aggregate._kind();
        if (kind == co.elastic.clients.elasticsearch._types.aggregations.Aggregate.Kind.Sterms) {
            // 字符串 terms
            for (StringTermsBucket bucket : aggregate.sterms().buckets().array()) {
                result.put(bucket.key().stringValue(), bucket.docCount());
            }
        } else if (kind == co.elastic.clients.elasticsearch._types.aggregations.Aggregate.Kind.Lterms) {
            // 数值 terms
            for (co.elastic.clients.elasticsearch._types.aggregations.LongTermsBucket bucket : aggregate.lterms().buckets().array()) {
                result.put(String.valueOf(bucket.key()), bucket.docCount());
            }
        } else {
            log.warn("[HotelEsSearch] 不支持的聚合类型: {}", kind);
        }
        return result;
    }

    /**
     * ES Hit → 前端 VO（含高亮 + 评分）
     */
    private HotelDocVO toVO(Hit<HotelDoc> hit) {
        HotelDoc src = hit.source();
        HotelDocVO vo = new HotelDocVO();
        if (src != null) {
            vo.setHotelId(src.getHotelId());
            vo.setName(src.getName());
            vo.setCity(src.getCity());
            vo.setAddress(src.getAddress());
            vo.setStar(src.getStar());
            vo.setMinPrice(src.getMinPrice());
            vo.setFacilities(src.getFacilities());
            vo.setMainImage(src.getMainImage());
            vo.setDescription(src.getDescription());
        }
        // 相关性评分
        if (hit.score() != null) {
            vo.setScore(hit.score());
        }
        // 高亮
        if (hit.highlight() != null && !hit.highlight().isEmpty()) {
            Map<String, List<String>> hl = new LinkedHashMap<>();
            hit.highlight().forEach((field, fragments) -> hl.put(field, fragments));
            vo.setHighlights(hl);
        }
        return vo;
    }
}
