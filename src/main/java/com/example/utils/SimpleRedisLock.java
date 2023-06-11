package com.example.utils;

import cn.hutool.core.lang.UUID;
import com.example.service.ILock;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock {
    private final String name;
    private final StringRedisTemplate stringRedisTemplate;
    private static final String KEY_PREFIX = "lock:";
    private static final String ID_PREFIX = UUID.randomUUID().toString(true) + "-";
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }
    //取锁
    @Override
    public boolean tryLock(long timeoutSec) {
        //获取线程ID
        String threadID = ID_PREFIX + Thread.currentThread().getId();
        Boolean result = stringRedisTemplate.opsForValue().setIfAbsent
                (KEY_PREFIX + this.name, threadID , timeoutSec, TimeUnit.SECONDS);
        //防止装箱拆箱产生空指针异常
        return Boolean.TRUE.equals(result);
    }
    //弃锁
    @Override
    public void unLock() {
        //调用lua脚本
        stringRedisTemplate.execute(UNLOCK_SCRIPT , Collections.singletonList(KEY_PREFIX + this.name) ,
                ID_PREFIX + Thread.currentThread().getId());
    }
    /*@Override
    public void unLock() {
        //获取线程ID
        String threadId =  ID_PREFIX  + Thread.currentThread().getId();
        //获取redis中存取的ID
        String id = stringRedisTemplate.opsForValue().get(KEY_PREFIX + this.name);
        //进行对比
        if (threadId.equals(id)){
            //释放锁
            stringRedisTemplate.delete(KEY_PREFIX + this.name);
        }
    }*/
}
