package com.atguigu.gmall.list.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.atguigu.gmall.list.repository.GoodsRepository;
import com.atguigu.gmall.list.service.SearchService;
import com.atguigu.gmall.model.list.*;
import com.atguigu.gmall.model.product.*;
import com.atguigu.gmall.product.client.ProductFeignClient;
import org.apache.lucene.search.join.ScoreMode;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.text.Text;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.MatchQueryBuilder;
import org.elasticsearch.index.query.Operator;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.nested.NestedAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.nested.ParsedNested;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedLongTerms;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedStringTerms;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SearchServiceImpl implements SearchService {
    @Autowired
    private RestHighLevelClient restHighLevelClient;

    @Autowired
    private ProductFeignClient productFeignClient;

    @Autowired
    private GoodsRepository goodsRepository;

    @Autowired
    private RedisTemplate redisTemplate;
    @Override
    public void upperGoods(Long skuId) {
        Goods goods = new Goods();
        // 通过skuId 查询平台属性信息
        List<BaseAttrInfo> baseAttrInfoList = productFeignClient.getAttrList(skuId);

        // 判断集合是否为空
        if (null!=baseAttrInfoList){
            List<SearchAttr> searchAttrList  = baseAttrInfoList.stream().map(baseAttrInfo -> {
                // 平台属性对象
                SearchAttr searchAttr = new SearchAttr();
                searchAttr.setAttrId(baseAttrInfo.getId());
                searchAttr.setAttrName(baseAttrInfo.getAttrName());

                // 获取平台属性值名称
                List<BaseAttrValue> attrValueList = baseAttrInfo.getAttrValueList();
                System.out.println(attrValueList.get(0).getValueName() + "检索");
                searchAttr.setAttrValue(attrValueList.get(0).getValueName());
                return searchAttr;
            }).collect(Collectors.toList());


            goods.setAttrs(searchAttrList);
        }
        //查询sku信息
        SkuInfo skuInfo = productFeignClient.getSkuInfo(skuId);
        // 查询品牌信息
        BaseTrademark trademark = productFeignClient.getTrademark(skuInfo.getTmId());
        if (trademark!=null){
            goods.setTmId(trademark.getId());
            goods.setTmName(trademark.getTmName());
        }
        // 查询分类
        BaseCategoryView baseCategoryView = productFeignClient.getCategoryView(skuInfo.getCategory3Id());
        if (baseCategoryView!=null){
            goods.setCategory1Id(baseCategoryView.getCategory1Id());
            goods.setCategory1Name(baseCategoryView.getCategory1Name());
            goods.setCategory2Id(baseCategoryView.getCategory2Id());
            goods.setCategory2Name(baseCategoryView.getCategory2Name());
            goods.setCategory3Id(baseCategoryView.getCategory3Id());
            goods.setCategory3Name(baseCategoryView.getCategory3Name());

        }
        goods.setDefaultImg(skuInfo.getSkuDefaultImg());
        goods.setPrice(skuInfo.getPrice().doubleValue());
        goods.setId(skuInfo.getId());
        goods.setTitle(skuInfo.getSkuName());
        goods.setCreateTime(new Date());

        this.goodsRepository.save(goods);

    }

    @Override
    public void lowerGoods(Long skuId) {
        this.goodsRepository.deleteById(skuId);
    }

    @Override
    public void incrHotScore(Long skuId) {

        String hotKey = "hotScore";

        // 更新热度
        Double hotScore = redisTemplate.opsForZSet().incrementScore(hotKey, "skuId:" + skuId, 1);
        if (hotScore%10==0){
            // 更新es
            // 查询原有es 的对象
            Optional<Goods> optional = this.goodsRepository.findById(skuId);
            Goods goods = optional.get();
            // hotScore 放入hotScore
            goods.setHotScore(Math.round(hotScore));
            goodsRepository.save(goods);
        }
    }

    @Override
    public SearchResponseVo search(SearchParam searchParam) throws IOException {
        // 构建 dsl 语句
        SearchRequest searchRequest = this.buildQueryDsl(searchParam);
        //  执行检索 同步执行
        SearchResponse response = this.restHighLevelClient.search(searchRequest, RequestOptions.DEFAULT);
        System.out.println(response);
        //  得到返回的结果集
        SearchResponseVo responseVO = this.parseSearchResult(response);
        responseVO.setPageSize(searchParam.getPageSize());
        responseVO.setPageNo(searchParam.getPageNo());
        // 获取总页数
        long totalPages = (responseVO.getTotal()+responseVO.getPageSize()-1)/responseVO.getPageSize();
        responseVO.setTotalPages(totalPages);
        return responseVO;
    }

    // 制作返回结果集
    private SearchResponseVo parseSearchResult(SearchResponse response) {
        SearchHits hits = response.getHits();
        // 声明对象
        SearchResponseVo searchResponseVo = new SearchResponseVo();
        // 获取品牌的集合
        Map<String, Aggregation> aggregationMap = response.getAggregations().asMap();
        // ParsedLongTerms ?
        ParsedLongTerms tmIdAgg = (ParsedLongTerms) aggregationMap.get("tmIdAgg");
        List<SearchResponseTmVo> trademarkList = tmIdAgg.getBuckets().stream().map(bucket -> {
            SearchResponseTmVo trademark = new SearchResponseTmVo();
            // 获取品牌Id
             trademark.setTmId((Long.parseLong(((Terms.Bucket) bucket).getKeyAsString())));
            //            trademark.setTmId(Long.parseLong(bucket.getKeyAsString()));
            // 获取品牌名称
            Map<String, Aggregation> tmIdSubMap = ((Terms.Bucket) bucket).getAggregations().asMap();
            ParsedStringTerms tmNameAgg = (ParsedStringTerms) tmIdSubMap.get("tmNameAgg");
            String tmName = tmNameAgg.getBuckets().get(0).getKeyAsString();

            trademark.setTmName(tmName);
            return trademark;
        }).collect(Collectors.toList());
        searchResponseVo.setTrademarkList(trademarkList);

        // 赋值商品列表
        SearchHit[] subHits = hits.getHits();
        List<Goods> goodsList = new ArrayList<>();
        if (subHits!=null && subHits.length>0){
            // 循环遍历
            for (SearchHit subHit : subHits) {
                // 将subHit 转换为对象
                Goods goods = JSONObject.parseObject(subHit.getSourceAsString(), Goods.class);

                // 获取高亮
                if (subHit.getHighlightFields().get("title")!=null){
                    Text title = subHit.getHighlightFields().get("title").getFragments()[0];
                    goods.setTitle(title.toString());
                }
                goodsList.add(goods);
            }
        }
        searchResponseVo.setGoodsList(goodsList);

        // 获取平台属性数据
        ParsedNested attrAgg = (ParsedNested) aggregationMap.get("attrAgg");
        ParsedLongTerms attrIdAgg = attrAgg.getAggregations().get("attrIdAgg");
        List<? extends Terms.Bucket> buckets = attrIdAgg.getBuckets();
        if (!CollectionUtils.isEmpty(buckets)){
            List<SearchResponseAttrVo> searchResponseAttrVOS = buckets.stream().map(bucket -> {
                // 声明平台属性对象
                SearchResponseAttrVo responseAttrVO = new SearchResponseAttrVo();
                // 设置平台属性值Id
                responseAttrVO.setAttrId(((Terms.Bucket) bucket).getKeyAsNumber().longValue());
                ParsedStringTerms attrNameAgg = bucket.getAggregations().get("attrNameAgg");
                List<? extends Terms.Bucket> nameBuckets = attrNameAgg.getBuckets();
                responseAttrVO.setAttrName(nameBuckets.get(0).getKeyAsString());
                // 设置规格参数列表
                ParsedStringTerms attrValueAgg = ((Terms.Bucket) bucket).getAggregations().get("attrValueAgg");
                List<? extends Terms.Bucket> valueBuckets = attrValueAgg.getBuckets();
                List<String> values = valueBuckets.stream().map(Terms.Bucket::getKeyAsString).collect(Collectors.toList());
                responseAttrVO.setAttrValueList(values);
                return responseAttrVO;
            }).collect(Collectors.toList());
            searchResponseVo.setAttrsList(searchResponseAttrVOS);
        }
        // 获取总记录数
        searchResponseVo.setTotal(hits.totalHits);

        return searchResponseVo;
    }

    // 制作dsl 语句
    private SearchRequest buildQueryDsl(SearchParam searchParam) {
        // 构建查询器
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        // 构建boolQueryBuilder
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        // 判断查询条件是否为空 关键字
        if (!StringUtils.isEmpty(searchParam.getKeyword())){
            // 小米手机  小米and手机
            // MatchQueryBuilder matchQueryBuilder = new MatchQueryBuilder("title",searchParam.getKeyword()).operator(Operator.AND);
            MatchQueryBuilder title = QueryBuilders.matchQuery("title", searchParam.getKeyword()).operator(Operator.AND);
            boolQueryBuilder.must(title);
        }
        // 构建品牌查询
        String trademark = searchParam.getTrademark();
        if (!StringUtils.isEmpty(trademark)){
            // trademark=2:华为
            String[] split = StringUtils.split(trademark, ":");
            if (split != null && split.length == 2) {
                // 根据品牌Id过滤
                boolQueryBuilder.filter(QueryBuilders.termsQuery("tmId", split[0]));
            }
        }

        // 构建分类过滤
        if(null!=searchParam.getCategory1Id()){
            boolQueryBuilder.filter(QueryBuilders.termsQuery("category1Id",searchParam.getCategory1Id().toString()));
        }
        // 构建分类过滤
        if(null!=searchParam.getCategory2Id()){
            boolQueryBuilder.filter(QueryBuilders.termsQuery("category2Id",searchParam.getCategory2Id().toString()));
        }
        // 构建分类过滤
        if(null!=searchParam.getCategory3Id()){
            boolQueryBuilder.filter(QueryBuilders.termsQuery("category3Id",searchParam.getCategory3Id().toString()));
        }

        // 构建平台属性查询
        // 23:4G:运行内存
        String[] props = searchParam.getProps();
        if (props!=null &&props.length>0){
            // 循环遍历
            for (String prop : props) {
                // 23:4G:运行内存
                String[] split = StringUtils.split(prop, ":");
                if (split!=null && split.length==3){
                    // 构建嵌套查询
                    BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
                    // 嵌套查询子查询
                    BoolQueryBuilder subBoolQuery = QueryBuilders.boolQuery();
                    // 构建子查==询中的过滤条件
                    subBoolQuery.must(QueryBuilders.termQuery("attrs.attrId",split[0]));
                    subBoolQuery.must(QueryBuilders.termQuery("attrs.attrValue",split[1]));
                    // ScoreMode.None ？
                    boolQuery.must(QueryBuilders.nestedQuery("attrs",subBoolQuery, ScoreMode.None));
                    // 添加到整个过滤对象中
                    boolQueryBuilder.filter(boolQuery);
                }
            }
        }
        // 执行查询方法
        searchSourceBuilder.query(boolQueryBuilder);
        // 构建分页
        int from = (searchParam.getPageNo()-1)*searchParam.getPageSize();
        searchSourceBuilder.from(from);
        searchSourceBuilder.size(searchParam.getPageSize());

        // 排序
        String order = searchParam.getOrder();
        if (!StringUtils.isEmpty(order)){
            // 判断排序规则
            String[] split = StringUtils.split(order, ":");
            if (split!=null && split.length==2){
                // 排序的字段
                String field = null;
                // 数组中的第一个参数
                switch (split[0]){
                    case "1":
                        field="hotScore";
                        break;
                    case "2":
                        field="price";
                        break;
                }
                searchSourceBuilder.sort(field,"asc".equals(split[1])? SortOrder.ASC:SortOrder.DESC);
            }else {
                // 没有传值的时候给默认值
                searchSourceBuilder.sort("hotScore",SortOrder.DESC);
            }
        }

        // 构建高亮
        HighlightBuilder highlightBuilder = new HighlightBuilder();
        highlightBuilder.field("title");
        highlightBuilder.postTags("</span>");
        highlightBuilder.preTags("<span style=color:red>");

        searchSourceBuilder.highlighter(highlightBuilder);

        //  设置品牌聚合
        TermsAggregationBuilder termsAggregationBuilder = AggregationBuilders.terms("tmIdAgg").field("tmId")
                .subAggregation(AggregationBuilders.terms("tmNameAgg").field("tmName"));
        searchSourceBuilder.aggregation(termsAggregationBuilder);

        //  设置平台属性聚合
        searchSourceBuilder.aggregation(AggregationBuilders.nested("attrAgg", "attrs")
                        .subAggregation(AggregationBuilders.terms("attrIdAgg").field("attrs.attrId")
                        .subAggregation(AggregationBuilders.terms("attrNameAgg").field("attrs.attrName"))
                        .subAggregation(AggregationBuilders.terms("attrValueAgg").field("attrs.attrValue"))));


        // 结果集过滤
        searchSourceBuilder.fetchSource(new String[]{"id","defaultImg","title","price"},null);

        SearchRequest searchRequest = new SearchRequest("goods");
        searchRequest.types("info");
        searchRequest.source(searchSourceBuilder);
        System.out.println("dsl:"+searchSourceBuilder.toString());
        return searchRequest;
    }
}
