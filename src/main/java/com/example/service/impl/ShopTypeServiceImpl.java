package com.example.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.alibaba.fastjson.JSONObject;
import com.example.dto.Result;
import com.example.entity.ShopType;
import com.example.mapper.ShopTypeMapper;
import com.example.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.example.utils.RedisConstants.CACHE_SHOP_TYPE_KEY;
import static com.example.utils.RedisConstants.CACHE_SHOP_TYPE_TTL;

/**
 * <p>
 *  服务实现类
 * </p>
 */
@Service
@Slf4j
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 查询缓存商铺
     * @return
     */
    @Override
    public Result queryForList() {
        //1，判断redis中是否有数据
        String s = stringRedisTemplate.opsForValue().get(CACHE_SHOP_TYPE_KEY);
        //2，判断是否存在
        if(StrUtil.isNotBlank(s)){
            //3，存在则返回
            List<ShopType> typeList = JSONObject.parseArray(s, ShopType.class);
            return Result.ok(typeList);
        }
        //4，不存在，则查询数据库
        List<ShopType> shopTypeList = this.query().orderByAsc("sort").list();
        //5，数据库不存在，返回404错误
        if (shopTypeList == null){
            return Result.fail("数据库无数据");
        }
        //6，存在则写入redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_TYPE_KEY ,
                JSONUtil.toJsonStr(shopTypeList) , CACHE_SHOP_TYPE_TTL , TimeUnit.MINUTES);
        //7，返回数据
        return Result.ok(shopTypeList);
    }
}
