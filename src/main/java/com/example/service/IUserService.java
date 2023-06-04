package com.example.service;


import com.baomidou.mybatisplus.extension.service.IService;
import com.example.dto.LoginFormDTO;
import com.example.dto.Result;
import com.example.entity.User;

import javax.servlet.http.HttpSession;

/**
 * <p>
 *  服务类
 * </p>
 */
public interface IUserService extends IService<User> {

    Result sendCode(String phone, HttpSession session);

    Result login(LoginFormDTO loginForm, HttpSession session);

    Result loginWithPassword(LoginFormDTO loginForm, HttpSession session);
}
