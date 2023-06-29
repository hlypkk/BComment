package com.example.controller;

import cn.hutool.core.bean.BeanUtil;
import com.example.dto.LoginFormDTO;
import com.example.dto.Result;
import com.example.dto.UserDTO;
import com.example.entity.User;
import com.example.entity.UserInfo;
import com.example.service.IUserInfoService;
import com.example.service.IUserService;
import com.example.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

/**
 * <p>
 * 前端控制器
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private IUserService userService;

    @Resource
    private IUserInfoService userInfoService;

    /**
     * 发送手机验证码
     */
    @PostMapping("code")
    public Result sendCode(@RequestParam("phone") String phone, HttpSession session) {
        //发送短信验证码并保存验证码

        return userService.sendCode(phone,session);
    }

    /**
     * 登录功能
     * @param loginForm 登录参数，包含手机号、验证码；或者手机号、密码
     */
    @PostMapping("/login")
    public Result login(@RequestBody LoginFormDTO loginForm, HttpSession session){
        //实现登录功能
        return userService.login(loginForm,session);
    }
    @PostMapping("/loginForPassword")
    public Result loginForPassword(@RequestBody LoginFormDTO loginForm, HttpSession session){
        return userService.loginWithPassword(loginForm,session);
    }
    /**
     * 登出功能
     * @return 无
     */
    @PostMapping("/logout")
    public Result logout(HttpServletRequest request){
        return userService.logout(request);
    }

    /**
     * 通过id查询
     * @param userId
     * @return
     */
    @GetMapping("/{id}")
    public Result queryUserById(@PathVariable("id") Long userId){
        return userService.queryUserById(userId);
    }
    // 获取当前登录的用户并返回
    @GetMapping("/me")
    public Result me(){
        UserDTO user = UserHolder.getUser();
        return Result.ok(user);
    }

    /**
     * 查询用户信息
     * @param userId
     * @return
     */
    @GetMapping("/info/{id}")
    public Result info(@PathVariable("id") Long userId){
        return userService.info(userId);
    }

    /**
     * 签到
     * @return
     */
    @PostMapping("/sign")
    public Result sign(){
        return userService.sign();
    }
    @GetMapping("/sign/count")
    public Result signCount(){
        return userService.signCount();
    }
}
