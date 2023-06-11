package com.example.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.example.dto.Result;
import com.example.entity.VoucherOrder;
import com.example.mapper.VoucherOrderMapper;
import com.example.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.utils.RedisIdWorker;
import com.example.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * <p>
 *  服务实现类
 * </p>
 */
@Service
@Slf4j
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Resource
    private SeckillVoucherServiceImpl seckillVoucherService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private RedissonClient redissonClient;
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();
    private IVoucherOrderService proxy;
    //类加载时自动提交任务
    @PostConstruct
    private void init(){
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }
    private final String queueName = "stream.orders";
    //lua脚本
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }
    //处理消息队列中的订单信息
    private class VoucherOrderHandler implements Runnable{
        @Override
        public void run() {
            while (true){
                try {
                    //1.获取消息队列中的订单信息
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create(queueName, ReadOffset.lastConsumed())
                    );
                    //2.判断消息是否获取成功
                    if (list == null || list.isEmpty()){
                        //3.失败，则进入下一次循环
                        continue;
                    }
                    //4.成功，下单
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> value = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);
                    //5.创建订单
                    handleVoucherOrder(voucherOrder);
                    // 6.ACK确认
                    stringRedisTemplate.opsForStream().acknowledge(queueName,"g1",record.getId());
                } catch (Exception e) {
                    log.error("订单异常:",e);
                    handlePendingList();
                }
            }
        }
    }

    /**
     * 异常消息回溯
     */
    private void handlePendingList() {
        while (true){
            try {
                //1.获取消息队列中的订单信息
                List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                        Consumer.from("g1", "c1"),
                        StreamReadOptions.empty().count(1),
                        StreamOffset.create(queueName, ReadOffset.from("0"))
                );
                //2.判断消息是否获取成功
                if (list == null || list.isEmpty()){
                    //3.失败，则进入下一次循环
                    break;
                }
                //4.成功，下单
                MapRecord<String, Object, Object> record = list.get(0);
                Map<Object, Object> value = record.getValue();
                VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);
                //5.创建订单
                handleVoucherOrder(voucherOrder);
                // 6.ACK确认
                stringRedisTemplate.opsForStream().acknowledge(queueName,"g1",record.getId());
            } catch (Exception e) {
                log.error("订单异常:",e);
                try {
                    Thread.sleep(20);
                } catch (InterruptedException ex) {
                    ex.printStackTrace();
                }
            }
        }
    }
    //阻塞队列
    /*private final BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024*1024);
    private class VoucherOrderHandler implements Runnable{
        @Override
        public void run() {
            while (true){
                try {
                    //1.获取阻塞队列中的订单信息
                    VoucherOrder voucherOrder = orderTasks.take();
                    //2.创建订单
                    handleVoucherOrder(voucherOrder);
                } catch (Exception e) {
                    log.error("订单异常:",e);
                }
            }
        }
    }*/

    /**
     * 判断当前订单是否符合要求
     * @param voucherOrder
     */
    private void handleVoucherOrder(VoucherOrder voucherOrder) {
        //1.获取用户id
        Long userId = voucherOrder.getUserId();
        //2.创建锁对象
        RLock clientLock = redissonClient.getLock("lock:order:" + userId);
        //3.获取锁
        boolean tryLock = clientLock.tryLock();
        //3.1获得锁失败
        if (!tryLock){
            log.error("不允许重复下单");
            return;
        }
        //3.2成功
        try {
            //获取代理对象
            proxy.createVoucherOrder(voucherOrder);
        }finally {
            //释放锁
            clientLock.unlock();
        }
    }

    /**
     * 秒杀抢购
     * @param voucherId
     * @return
     */
    @Override
    public Result seckillVoucher(Long voucherId) {
        //用户id
        Long userId = UserHolder.getUser().getId();
        //订单id
        long orderId = redisIdWorker.nextId("order");
        //1.执行lua脚本
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(),
                userId.toString(),
                String.valueOf(orderId)
        );
        assert result != null;
        int value = result.intValue();
        //2.判断结果是否为0
        if (value != 0){
            return Result.fail( value == 1 ? "库存不足" : "不能重复下单");
        }
        /*//3.为0，有购买资格，把下单信息保存到阻塞队列
        VoucherOrder voucherOrder = new VoucherOrder();
        //订单id
        voucherOrder.setId(orderId);
        //用户id
        voucherOrder.setUserId(userId);
        //代金券id
        voucherOrder.setVoucherId(voucherId);
        //4.存入阻塞队列
        orderTasks.add(voucherOrder);*/
        //5.获取代理对象
        proxy = (IVoucherOrderService) AopContext.currentProxy();
        //6.返回订单id
        return Result.ok(orderId);
    }

    /**
     * 秒杀抢购
     * @param voucherId
     * @return
     */
     /*@Override
    public Result seckillVoucher(Long voucherId) {
        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
        //1,判断秒杀是否开始
        if (seckillVoucher.getBeginTime().isAfter(LocalDateTime.now())){
            return Result.fail("秒杀尚未开始");
        }
        //2,判断秒杀是否结束
        if (seckillVoucher.getEndTime().isBefore(LocalDateTime.now())){
            return Result.fail("秒杀已经结束");
        }
        //3,判断秒杀券是否还有库存
        if (seckillVoucher.getStock() < 1){
            return Result.fail("券已售罄");
        }
        //4,获得当前线程ID
        Long id = UserHolder.getUser().getId();
        //获取加锁
        //synchronized (id.toString().intern()){} 一人一单锁，多线程下失效
        //SimpleRedisLock redisLock = new SimpleRedisLock("order:" + id, stringRedisTemplate); 自定义分布式锁
        RLock clientLock = redissonClient.getLock("lock:order:" + id);
        boolean tryLock = clientLock.tryLock();
        //获得锁失败
        if (!tryLock){
            return Result.fail("一人限购一单");
        }
        try {
            //获取代理对象
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        }finally {
            //释放锁
            clientLock.unlock();
        }
    }*/

    /**
     * 创建秒杀订单
     * @param voucherOrder
     */
    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder){
        //1,一人一单
        Long id = voucherOrder.getUserId();
        //2,查询订单
        Integer count = query().eq("user_id", id).eq("voucher_id", voucherOrder.getVoucherId()).count();
        //3,判断是否存在
        if (count > 0){
            log.error("不允许重复下单");
            return;
        }
        //4,以上都通过则扣减库存
        boolean success = seckillVoucherService.update().
                setSql("stock = stock - 1").
                eq("voucher_id", voucherOrder.getVoucherId()).
                gt("stock",0).
                update();
        if (!success){
            log.error("券已售空");
            return ;
        }
        //5,写入数据库
        this.save(voucherOrder);
    }
}
