package com.atguigu.gmall.product.service;

import com.atguigu.gmall.model.product.BaseTrademark;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface BaseTrademarkService extends IService<BaseTrademark> {

    /**
     * pageParam 查询条件
     * @param pageParam
     * @return
     */
    IPage<BaseTrademark> selectPage(Page<BaseTrademark> pageParam);

    /**
     * 查询所有的品牌
     * @return
     */
    List<BaseTrademark> getTrademarkList();

    // 根据Id 查询品牌内容
    BaseTrademark getById(String id);

}
