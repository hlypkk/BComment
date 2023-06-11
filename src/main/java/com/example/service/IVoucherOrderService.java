package com.example.service;


import com.baomidou.mybatisplus.extension.service.IService;
import com.example.dto.Result;
import com.example.entity.VoucherOrder;

/**
 * <p>
 *  服务类
 * </p>
 */
public interface IVoucherOrderService extends IService<VoucherOrder> {
    Result seckillVoucher(Long voucherId);

    void createVoucherOrder(VoucherOrder voucherId);
}
