package com.example.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.dto.Result;
import com.example.entity.Follow;

/**
 * <p>
 *  服务类
 * </p>
 */
public interface IFollowService extends IService<Follow> {

    Result follow(Long followUserId, Boolean isFollow);

    Result isFollow(Long followUserId);

    Result followCommons(Long id);
}
