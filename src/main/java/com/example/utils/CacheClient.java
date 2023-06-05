package com.example.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.example.utils.RedisConstants.*;

@Component
@Slf4j
public class CacheClient {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 普通set方法
     * @param key
     * @param value
     * @param time
     * @param unit
     */
    public void set(String key, Object value, Long time, TimeUnit unit){
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value),time,unit);
    }

    /**
     * 自定义逻辑过期时间set方法
     * @param key
     * @param value
     * @param time
     * @param unit
     */
    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) throws InterruptedException {
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(redisData));
    }

    /**
     * 通过写入null值解决缓存穿透
     * @param keyPrefix
     * @param id
     * @param type
     * @param dbFallBack
     * @param time
     * @param unit
     * @param <R>
     * @param <ID>
     * @return
     */
    public <R,ID> R  queryWithPassThrough(String keyPrefix , ID id , Class<R> type
            , Function<ID,R> dbFallBack , Long time, TimeUnit unit) {
        String key = keyPrefix + id;
        //1，从redis查缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        //2，判断是否存在
        if (StrUtil.isNotBlank(json)) {
            //2.1，存在则直接返回
            return JSONUtil.toBean(json, type);
        }
        //2.2,判断命中的是否是空值
        if (json != null) {
            return null;
        }
        //3，不存在，根据id查数据库
        R r = dbFallBack.apply(id);
        //4，数据库不存在，返回404错误
        if (r == null) {
            //4.1,将空值写入redis
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        //5，存在则写入redis
        this.set(key , r , time , unit);
        //6，返回数据
        return r;
    }

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    /**
     * 逻辑过期解决缓存击穿
     * @param keyPrefix
     * @param id
     * @param type
     * @param dbFallBack
     * @param time
     * @param unit
     * @param <R>
     * @param <ID>
     * @return
     */
    public <R,ID> R queryWithLogicalExpire(String keyPrefix ,
           ID id , Class<R> type , Function<ID,R> dbFallBack , Long time, TimeUnit unit) {
        String key = keyPrefix + id;
        //1.从redis查缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        //2.判断是否存在
        //3，不存在则直接返回
        if (StrUtil.isBlank(json)) {
            return null;
        }
        //4.命中，需要先把json反序列化为对象
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        LocalDateTime expireTime = redisData.getExpireTime();
        //5.判断是否过期
        if (expireTime.isAfter(LocalDateTime.now())){
            //5.1.未过期则直接返回旧店铺信息
            return r;
        }
        //5.2.已过期，则需要缓存重建
        //6.缓存重建
        //6.1.获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        boolean lock = tryGetLock(lockKey);
        //6.2.判断是否获取成功
        if (lock){
            //再次判断是否过期
            if (!expireTime.isAfter(LocalDateTime.now())){
                //6.3.获取成功，开启独立线程，实现缓存重建
                CACHE_REBUILD_EXECUTOR.submit(() -> {
                    try {
                        //缓存重建
                        R rr = dbFallBack.apply(id);
                        this.setWithLogicalExpire(key , rr , time , unit);
                    }catch (Exception e){
                        throw new RuntimeException();
                    }finally {
                        //释放锁
                        unlock(lockKey);
                    }
                });
           }
        }
        //6.4.失败，则返回过期信息
        return r;
    }

    /**
     * 互斥锁解决缓存击穿
     * @param keyPrefix
     * @param id
     * @param type
     * @param dbFallBack
     * @param time
     * @param unit
     * @param <R>
     * @param <ID>
     * @return
     */
    public <R,ID> R queryWithMutex(String keyPrefix ,
        ID id , Class<R> type , Function<ID,R> dbFallBack , Long time, TimeUnit unit) {
        String key = keyPrefix + id;
        //1，从redis查缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        //2，判断是否存在
        if (StrUtil.isNotBlank(json)) {
            //2.1，存在则直接返回
            return JSONUtil.toBean(json, type);
        }
        //3,判断命中的是否是空值
        if (json != null) {
            return null;
        }
        //4，实现缓存重建
        String lockKey = LOCK_SHOP_KEY + id;
        R r;
        try {
            //4.1,获取互斥锁
            boolean getLock = tryGetLock(lockKey);
            //4.2,判断是否获取成功
            if (!getLock) {
                //4.2.1,失败则休眠并重试
                Thread.sleep(50);
                return queryWithMutex(keyPrefix, id, type, dbFallBack, time, unit);
            }
            //4.2.2,成功，根据id查询数据库
            //从redis查缓存
            String s1 = stringRedisTemplate.opsForValue().get(key);
            //判断是否存在
            if (StrUtil.isNotBlank(s1)) {
                //存在则直接返回
                return JSONUtil.toBean(s1, type);
            }
            r = dbFallBack.apply(id);
            Thread.sleep(200);
            //5，数据库不存在，返回404错误
            if (r == null) {
                //5.1,将空值写入redis
                stringRedisTemplate.opsForValue().set(key,
                        "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            //6，存在则写入redis
            this.set(key, r, time, unit);
        } catch (InterruptedException e) {
            throw new RuntimeException();
        } finally {
            //7，释放互斥锁
            unlock(lockKey);
        }
        //8，返回数据
        return r;
    }

    //添加锁
    public boolean tryGetLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    //释放锁
    public void unlock(String key) {
        stringRedisTemplate.delete(key);
    }
}
