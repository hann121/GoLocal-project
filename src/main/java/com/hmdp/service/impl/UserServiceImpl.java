package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /*
    * 发送验证码
    * */
    @Override
    public Result sendCode(String phone, HttpSession session) {
        //验证手机号
        if(RegexUtils.isPhoneInvalid(phone)){
            return Result.fail("手机格式错误");
        }
        //生成验证码
        String code = RandomUtil.randomNumbers(6);
        //保存到session
//        session.setAttribute(LOGIN_CODE_KEY+phone,code);
        //使用redis缓存
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY+phone,code,LOGIN_CODE_TTL, TimeUnit.MINUTES);
        log.info("验证码:{}",code);
        return Result.ok();
    }

    /*
    * 实现登录
    * */
    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
//        String phone = loginForm.getPhone();
//        Object sessionPhone = session.getAttribute("phone");
//        if(sessionPhone==null || !phone.equals(sessionPhone.toString())){
//            return Result.fail("手机号与原先的不匹配!");
//        }
//        Object sessionCode = session.getAttribute("code");
//        String code = loginForm.getCode();
//        if(sessionCode==null || !code.equals(sessionCode.toString())){
//            return Result.fail("验证码不正确!");
//        }
        String phone = loginForm.getPhone();
        String code = loginForm.getCode();

        if(RegexUtils.isPhoneInvalid(phone)){
            return Result.fail("手机号格式错误!");
        }
        //此处已实现手机号码和验证码一对一
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY+phone);

        if(cacheCode==null || !cacheCode.equals(code)){
            return Result.fail("验证码不正确!");
        }
        //查询用户
        User user = query().eq("phone",phone).one();
        if(user == null){
            user = new User();
            user.setPhone(phone);
            user.setNickName(USER_NICK_NAME_PREFIX+RandomUtil.randomString(8));
            user.setUpdateTime(LocalDateTime.now());
            //mp保存到数据库
            save(user);
        }
        //封装用户信息，生成token
        String token = UUID.randomUUID().toString();
        log.info("token值为:{}",token);
        UserDTO userDTO = new UserDTO();
        BeanUtils.copyProperties(user,userDTO);
        //封装成map，以便后续hash
        Map<String,Object> userMap = BeanUtil.beanToMap(userDTO);
        //转为string，符合序列化器
        userMap.put("id",userDTO.getId().toString());
        //hash
        stringRedisTemplate.opsForHash().putAll(LOGIN_USER_KEY+token,userMap);
        //设置过期时间
        stringRedisTemplate.expire(LOGIN_USER_KEY+token,LOGIN_USER_TTL,TimeUnit.MINUTES);

        //        session.setAttribute("user",user);
        return Result.ok(token);
    }
}
