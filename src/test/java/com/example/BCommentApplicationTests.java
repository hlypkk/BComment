package com.example;

import com.example.entity.Shop;
import com.example.service.impl.ShopServiceImpl;
import com.example.utils.CacheClient;
import com.example.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.example.utils.RedisConstants.CACHE_SHOP_KEY;
import static com.example.utils.RedisConstants.SHOP_GEO_KEY;

@SpringBootTest
class BCommentApplicationTests {
    @Resource
    private ShopServiceImpl shopService;
    @Resource
    private CacheClient cacheClient;
    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private final ExecutorService es = Executors.newFixedThreadPool(500);

    /**
     * 测试id生成器
     * @throws Exception
     */
    @Test
    void testIdWorker() throws Exception {
        CountDownLatch latch = new CountDownLatch(300);
        Runnable task = () -> {
            for (int i = 0; i < 100; i++) {
                long order = redisIdWorker.nextId("order");
                System.out.println("id = " + order);
            }
            latch.countDown();
        };
        long begin = System.currentTimeMillis();
        for (int i = 0; i < 300; i++) {
            es.submit(task);
        }
        latch.await();
        long end = System.currentTimeMillis();
        System.out.println("time = " + (end - begin));
    }
    @Test
    void testSaveShop(){
        Shop shop = shopService.getById(1L);
        cacheClient.setWithLogicalExpire(CACHE_SHOP_KEY + 1L, shop, 10L, TimeUnit.SECONDS);
    }

    /**
     * 加载店铺地址
     */
    @Test
    void loadShopData(){
        //1. 查询店铺信息
        List<Shop> list = shopService.list();
        //2. 把店铺分组，按照typeId分类
        Map<Long, List<Shop>> collect = list.stream().collect(Collectors.groupingBy(Shop::getTypeId));
        //3. 分批写入redis
        for (Map.Entry<Long, List<Shop>> entry : collect.entrySet()) {
            //3.1 获取类型id
            Long typeId = entry.getKey();
            String key = SHOP_GEO_KEY + typeId;
            //3.2 获取同类型店铺集合
            List<Shop> value = entry.getValue();
            //3.3 建立地址集合
            List<RedisGeoCommands.GeoLocation<String>> locations = new ArrayList<>(value.size());
            //3.4 循环写入redis
            for (Shop shop : value) {
                locations.add(new RedisGeoCommands.GeoLocation<>(
                        shop.getId().toString(),
                        new Point(shop.getX(),shop.getY())
                ));
            }
            stringRedisTemplate.opsForGeo().add(key,locations);
        }
    }
}
