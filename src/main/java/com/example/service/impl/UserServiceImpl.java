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
import com.example.entity.UserInfo;
import com.example.mapper.UserMapper;
import com.example.service.IUserInfoService;
import com.example.service.IUserService;
import com.example.utils.RegexUtils;
import com.example.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
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
    @Resource
    private IUserInfoService userInfoService;
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
     * 登出功能
     * @param request
     * @return
     */
    @Override
    public Result logout(HttpServletRequest request) {
        String token = request.getHeader("authorization");
        String tokenKey = LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().delete(tokenKey);
        return Result.ok();
    }

    /**
     * 查询用户详情
     * @param userId
     * @return
     */
    @Override
    public Result info(Long userId) {
        // 查询详情
        UserInfo info = userInfoService.getById(userId);
        if (info == null) {
            // 没有详情，应该是第一次查看详情
            return Result.ok();
        }
        info.setCreateTime(null);
        info.setUpdateTime(null);
        // 返回
        return Result.ok(info);
    }

    /**
     * 通过id查询
     * @param userId
     * @return
     */
    @Override
    public Result queryUserById(Long userId) {
        User user = getById(userId);
        if (user == null){
            return Result.ok();
        }
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        return Result.ok(userDTO);
    }

    /**
     * 签到
     * @return
     */
    @Override
    public Result sign() {
        // 1.获取当前登录用户
        Long userId = UserHolder.getUser().getId();
        // 2.获取日期
        LocalDateTime now = LocalDateTime.now();
        // 3.拼接key
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + userId + keySuffix;
        // 4.获取今天是本月的第几天
        int dayOfMonth = now.getDayOfMonth();
        stringRedisTemplate.opsForValue().setBit(key , dayOfMonth - 1 , true);
        return Result.ok();
    }

    @Override
    public Result signCount() {
        // 1.获取当前登录用户
        Long userId = UserHolder.getUser().getId();
        // 2.获取日期
        LocalDateTime now = LocalDateTime.now();
        // 3.拼接key
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + userId + keySuffix;
        // 4.获取今天是本月的第几天
        int dayOfMonth = now.getDayOfMonth();
        // 5.获取本月截止今天为止的所有的签到记录，返回的是一个十进制的数字 BITFIELD sign:5:202203 GET u14 0
        List<Long> result = stringRedisTemplate.opsForValue().bitField(key,
                BitFieldSubCommands.create().get
                        (BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0));
        if (result == null || result.isEmpty()){
            // 没有任何签到结果
            return Result.ok(0);
        }
        Long num = result.get(0);
        if (num == null || num == 0){
                return Result.ok(0);
        }
        // 6.循环遍历
        int count = 0;
        while (true){
            // 6.1.让这个数字与1做与运算，得到数字的最后一个bit位  // 判断这个bit位是否为0
            if ((num & 1) == 0){
                // 如果为0，说明未签到，结束
                break;
            }else {
                // 如果不为0，说明已签到，计数器+1
                count++;
            }
            // 把数字右移一位，抛弃最后一个bit位，继续下一个bit位
            count >>>= 1;
        }
        return Result.ok(count);
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
