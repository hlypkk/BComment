package com.example.controller;

import com.example.dto.Result;
import com.example.service.IShopTypeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import javax.annotation.Resource;

/**
 * <p>
 * 前端控制器
 * </p>
 */
@RestController
@RequestMapping("/shop-type")
public class ShopTypeController {
    @Resource
    private IShopTypeService typeService;

    /**
     * 查询缓存商铺
     * @return
     */
    @GetMapping("list")
    public Result queryTypeList() {
        /*List<ShopType> typeList = typeService
                .query().orderByAsc("sort").list();*/
        return typeService.queryForList();
    }
}
