package com.example.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.dto.LoginFormDTO;
import com.example.dto.Result;
import com.example.dto.UserDTO;
import com.example.entity.User;
import com.example.mapper.UserMapper;
import com.example.service.IUserService;
import com.example.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import static com.example.utils.RedisConstants.*;
import static com.example.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    /**
     * 发送验证码
     * @param phone
     * @param session
     * @return
     */
    @Override
    public Result sendCode(String phone, HttpSession session) {
        //1,校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误！！！！");
        }
        //2,生成验证码
        String code = RandomUtil.randomNumbers(6);
        //3,保存验证码到redis
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone , code , LOGIN_CODE_TTL , TimeUnit.MINUTES);
        //4,发送验证码
        log.debug("验证码:{}",code);
        return Result.ok();
    }

    /**
     * 用户验证码登录
     * @param loginForm
     * @param session
     * @return
     */
    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //1,校验手机号
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)){
            return Result.fail("手机号格式错误！！！");
        }
        //2,校验验证码
        String code = loginForm.getCode();
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        if (cacheCode == null || !cacheCode.equals(code)){
            return Result.fail("验证码错误！！！");
        }
        //3,查询用户是否存在
        User user = query().eq("phone", phone).one();
        if (user == null){
            //4,不存在则创建新用户
            user = creatUserWithPhone(phone);
        }
        //5,将用户保存至redis
        //5.1,随机生成token，作为登录令牌
        String token = UUID.randomUUID().toString(true);
        //5.2,将user对象转为Hash存储
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> stringObjectMap = BeanUtil.beanToMap(userDTO,new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true).
                        setFieldValueEditor((fieldName,fieldValue) -> fieldValue.toString()));
        //5.3,存储
        String tokenKey = LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey , stringObjectMap);
        //5.4,设置token有效期
        stringRedisTemplate.expire(tokenKey , LOGIN_USER_TTL , TimeUnit.MINUTES);
        //6,返回token
        return Result.ok(token);
    }

    /**
     * 用户密码登录
     * @param loginForm
     * @param session
     * @return
     */
    @Override
    public Result loginWithPassword(LoginFormDTO loginForm, HttpSession session) {
        //1,校验手机号
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)){
            return Result.fail("手机号格式错误！！！");
        }
        //2,校验密码
        String password = loginForm.getPassword();
        QueryWrapper<User>  queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("phone",phone).eq("password",password);
        User one = this.getOne(queryWrapper);
        if (one == null){
            return  Result.fail("用户名或密码错误");
        }
        //3,将用户保存至redis
        //3.1,随机生成token，作为登录令牌
        String token = UUID.randomUUID().toString(true);
        //3.2,将user对象转为Hash存储
        UserDTO userDTO = BeanUtil.copyProperties(one, UserDTO.class);
        Map<String, Object> stringObjectMap = BeanUtil.beanToMap(userDTO,new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true).
                        setFieldValueEditor((fieldName,fieldValue) -> fieldValue.toString()));
        //3.3,存储
        String tokenKey = LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey , stringObjectMap);
        //3.4,设置token有效期
        stringRedisTemplate.expire(tokenKey , LOGIN_USER_TTL , TimeUnit.MINUTES);
        //4,返回token
        return Result.ok(token);
    }

    /**
     * 注册
     * @param phone
     * @return
     */
    private User creatUserWithPhone(String phone) {
        User user = new User();
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        user.setPassword("123456");
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(6));
        this.save(user);
        return user;
    }
}
