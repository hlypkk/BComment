package com.example.service.impl;

import com.example.dto.Result;
import com.example.entity.SeckillVoucher;
import com.example.entity.VoucherOrder;
import com.example.mapper.VoucherOrderMapper;
import com.example.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.utils.RedisIdWorker;
import com.example.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * <p>
 *  服务实现类
 * </p>
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Resource
    private SeckillVoucherServiceImpl seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Override
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
        //4,返回订单id
        Long id = UserHolder.getUser().getId();
        //*加锁
        synchronized (id.toString().intern()){
            //*获取代理对象
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        }
    }
    @Transactional
    public Result createVoucherOrder(Long voucherId){
        //1,一人一单
        Long id = UserHolder.getUser().getId();
        //2,查询订单
        Integer count = query().eq("user_id", id).eq("voucher_id", voucherId).count();
        //3,判断是否存在
        if (count > 0){
            return Result.fail("一人限购一单");
        }
        //4,以上都通过则扣减库存
        boolean success = seckillVoucherService.update().
                setSql("stock = stock - 1").
                eq("voucher_id", voucherId).
                gt("stock",0).
                update();
        if (!success){
            return Result.fail("券已售罄");
        }
        //5,创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        //6,订单id
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        //7,用户id
        Long userId = UserHolder.getUser().getId();
        voucherOrder.setUserId(userId);
        //8,代金券id
        voucherOrder.setVoucherId(voucherId);
        //9,写入数据库
        this.save(voucherOrder);
        return Result.ok(orderId);
    }
}
